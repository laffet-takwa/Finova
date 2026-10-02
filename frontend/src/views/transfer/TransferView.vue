<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, Info, Search, ShieldCheck, Wallet } from 'lucide-vue-next'
import PageHeader from '@/components/ui/PageHeader.vue'
import StepProgress, { type StepProgressItem } from '@/components/ui/StepProgress.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseInput from '@/components/ui/BaseInput.vue'
import BaseSelect, { type SelectOption } from '@/components/ui/BaseSelect.vue'
import DetailRow from '@/components/ui/DetailRow.vue'
import Skeleton from '@/components/ui/Skeleton.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import { useAccountStore } from '@/stores/accountStore'
import { useTransactionStore, type TransferDraft } from '@/stores/transactionStore'
import { accountApi, newIdempotencyKey } from '@/api'
import { AppError } from '@/utils/errors'
import { formatAmount, formatMoney } from '@/utils/format'
import type { Account, BeneficiaryLookup } from '@/types'

const STEPS: StepProgressItem[] = [
  { label: 'Recipient' },
  { label: 'Amount' },
  { label: 'Review' },
  { label: 'Complete' },
]

const MIN_AMOUNT = 0.001
const MAX_AMOUNT = 1_000_000
const MAX_DESCRIPTION = 255

/** `found` — resolved · `rejected` — the destination cannot be paid · `unavailable` — retry later. */
type LookupOutcome = 'found' | 'rejected' | 'unavailable'

const route = useRoute()
const router = useRouter()
const accountStore = useAccountStore()
const transactionStore = useTransactionStore()

const loading = ref(true)
const loadError = ref<string | null>(null)
const loadOffline = ref(false)

const senderAccountId = ref('')
const recipientNumber = ref('')
const recipientError = ref<string | null>(null)
const recipient = ref<BeneficiaryLookup | null>(null)
const verifying = ref(false)
const amountText = ref('')
const amountError = ref<string | null>(null)
const description = ref('')
const formError = ref<string | null>(null)

const existingDraft = computed(() => transactionStore.transferDraft)
const activeAccounts = computed(() => accountStore.activeAccounts)
/** Three or fewer accounts read better as cards than as a select. */
const useAccountCards = computed(() => activeAccounts.value.length > 0 && activeAccounts.value.length <= 3)

const sender = computed<Account | null>(
  () => activeAccounts.value.find((account) => account.id === senderAccountId.value) ?? null,
)

const senderOptions = computed<SelectOption[]>(() =>
  activeAccounts.value.map((account) => ({
    value: account.id,
    label: `${account.nickname ?? 'Account'} · ${account.maskedAccountNumber} · ${formatMoney(account.availableBalance, account.currency)}`,
  })),
)

function accountLabel(account: Account): string {
  return account.nickname ?? (account.accountType === 'SAVINGS' ? 'Savings Account' : 'Everyday Account')
}

/** `250`, `250.5` and `250,5` all parse; anything else resolves to `null`. */
const amountValue = computed<number | null>(() => {
  const cleaned = amountText.value.replace(/\s/g, '').replace(',', '.').replace(/[^\d.]/g, '')
  if (cleaned.length === 0) return null
  const parsed = Number(cleaned)
  return Number.isFinite(parsed) ? parsed : null
})

const remainingBalance = computed<number | null>(() => {
  if (!sender.value) return null
  return Number((sender.value.availableBalance - (amountValue.value ?? 0)).toFixed(3))
})

const currencyMismatch = computed(
  () => Boolean(recipient.value && sender.value && recipient.value.currency !== sender.value.currency),
)

/** Step 2 becomes current as soon as the recipient has been verified. */
const currentStep = computed(() => (recipient.value ? 2 : 1))

const amountSummary = computed(() =>
  formatMoney(amountValue.value ?? 0, sender.value?.currency ?? 'TND'),
)

function resetRecipient(): void {
  recipient.value = null
  recipientError.value = null
}

/**
 * Explicit change handlers instead of watchers: hydration from a restored draft
 * must not wipe the beneficiary it just restored.
 */
function onRecipientInput(value: string): void {
  recipientNumber.value = value
  resetRecipient()
  formError.value = null
}

function onSenderInput(value: string): void {
  senderAccountId.value = value
  resetRecipient()
}

async function load(force = false): Promise<void> {
  loading.value = true
  loadError.value = null
  try {
    await accountStore.fetchAll(force)
  } catch (cause) {
    loadError.value = cause instanceof Error ? cause.message : 'We could not load your accounts.'
    loadOffline.value = cause instanceof AppError ? cause.isNetworkError : false
  } finally {
    loading.value = false
  }
}

async function lookupRecipient(number: string): Promise<{ outcome: LookupOutcome; data: BeneficiaryLookup | null }> {
  verifying.value = true
  recipientError.value = null
  try {
    const result = await accountApi.lookupBeneficiary(number.replace(/\s/g, ''))
    if (result.status !== 'ACTIVE') {
      recipient.value = null
      recipientError.value = `This account cannot receive transfers because it is ${result.status.toLowerCase()}.`
      return { outcome: 'rejected', data: null }
    }
    recipient.value = result
    return { outcome: 'found', data: result }
  } catch (cause) {
    resetRecipient()
    if (cause instanceof AppError) {
      if (cause.code === 'ACCOUNT_NOT_FOUND') {
        recipientError.value = 'No Finova account matches that number. Check the digits and try again.'
        return { outcome: 'rejected', data: null }
      }
      if (cause.code === 'VALIDATION_ERROR' || cause.code === 'SENDER_RECEIVER_IDENTICAL') {
        recipientError.value = 'The source and destination accounts must be different.'
        return { outcome: 'rejected', data: null }
      }
      if (cause.isNetworkError) {
        recipientError.value =
          'We could not reach Finova to verify this account. You can still continue — we verify again before sending.'
        return { outcome: 'unavailable', data: null }
      }
      recipientError.value = cause.message
      return { outcome: 'unavailable', data: null }
    }
    recipientError.value = 'We could not verify this account right now.'
    return { outcome: 'unavailable', data: null }
  } finally {
    verifying.value = false
  }
}

async function onVerify(): Promise<void> {
  const number = recipientNumber.value.trim()
  if (number.length < 4) {
    recipientError.value = 'Enter the recipient account number first.'
    return
  }
  await lookupRecipient(number)
}

function validateAmount(): boolean {
  const amount = amountValue.value
  if (amount === null) {
    amountError.value = 'Enter the amount you want to send.'
    return false
  }
  if (amount <= 0) {
    amountError.value = 'The amount must be greater than zero.'
    return false
  }
  if (amount < MIN_AMOUNT) {
    amountError.value = `The minimum transfer amount is ${formatMoney(MIN_AMOUNT, sender.value?.currency ?? 'TND')}.`
    return false
  }
  if (amount > MAX_AMOUNT) {
    amountError.value = `The maximum transfer amount is ${formatMoney(MAX_AMOUNT, sender.value?.currency ?? 'TND')}.`
    return false
  }
  if (!sender.value) {
    amountError.value = 'Choose the account you are sending from.'
    return false
  }
  if (amount > sender.value.availableBalance) {
    amountError.value = `Insufficient balance for this transfer. ${formatMoney(sender.value.availableBalance, sender.value.currency)} is available.`
    return false
  }
  amountError.value = null
  return true
}

async function onContinue(): Promise<void> {
  formError.value = null
  resetRecipient()

  if (!validateAmount()) return

  const number = recipientNumber.value.replace(/\s/g, '').trim()
  if (number.length < 4) {
    formError.value = 'Enter the account number of the person or business you are paying.'
    return
  }

  // Verify on continue when the user skipped the explicit check. Only a hard
  // rejection blocks; an unreachable service does not, because the backend
  // revalidates the transfer anyway.
  const lookup = await lookupRecipient(number)
  if (lookup.outcome === 'rejected') return

  const currentSender = sender.value
  if (!currentSender) {
    formError.value = 'Choose the account you are sending from.'
    return
  }
  if (lookup.data && lookup.data.currency !== currentSender.currency) {
    amountError.value = `Transfers are only possible between accounts of the same currency. This account holds ${lookup.data.currency}, your account holds ${currentSender.currency}.`
    return
  }

  const next: TransferDraft = {
    senderAccountId: currentSender.id,
    receiverAccountNumber: lookup.data?.accountNumber ?? recipientNumber.value.trim(),
    amount: amountValue.value ?? 0,
    currency: currentSender.currency,
    description: description.value.trim(),
    // Reuse the key minted earlier for this same logical transfer: a retry must
    // never be able to produce a second debit.
    idempotencyKey: existingDraft.value?.idempotencyKey ?? newIdempotencyKey(),
    recipient: lookup.data,
    createdAt: new Date().toISOString(),
  }
  transactionStore.setTransferDraft(next)

  await router.push({
    name: 'transfer-review',
    query: {
      s: next.senderAccountId,
      r: next.receiverAccountNumber,
      a: next.amount.toString(),
      c: next.currency,
      d: next.description,
      k: next.idempotencyKey,
    },
  })
}

onMounted(async () => {
  await load()

  const draft = existingDraft.value
  const requested = typeof route.query.account === 'string' ? route.query.account : null
  const candidate = requested ?? draft?.senderAccountId ?? accountStore.defaultSenderAccount?.id ?? ''
  if (activeAccounts.value.some((account) => account.id === candidate)) senderAccountId.value = candidate

  if (draft) {
    senderAccountId.value = draft.senderAccountId
    recipientNumber.value = draft.receiverAccountNumber
    amountText.value = draft.amount.toFixed(3)
    description.value = draft.description
    recipient.value = draft.recipient
    recipientError.value = null
  }
})
</script>

<template>
  <div class="space-y-5 pb-4">
    <PageHeader
      eyebrow="Transfers"
      title="Send money"
      description="Transfer funds securely to another Finova account."
    />

    <!-- Loading -->
    <div v-if="loading" class="fin-card space-y-4 p-5" aria-busy="true">
      <Skeleton variant="block" :rows="4" />
      <span class="sr-only">Loading your accounts</span>
    </div>

    <!-- Error -->
    <ErrorState
      v-else-if="loadError"
      class="fin-card"
      title="We could not load your accounts"
      :description="loadError"
      :offline="loadOffline"
      retry-label="Reload accounts"
      @retry="load(true)"
    />

    <!-- Nothing to send from -->
    <div v-else-if="activeAccounts.length === 0" class="fin-card">
      <EmptyState
        title="No active accounts"
        description="You need at least one active Finova account before you can send money. Open one, then come back to this page."
        :icon="Wallet"
        action-label="Open an account"
        @action="router.push({ name: 'accounts' })"
      />
    </div>

    <!-- Form -->
    <template v-else>
      <section class="fin-card p-4 sm:p-5">
        <StepProgress :steps="STEPS" :current="currentStep" aria-label="Transfer progress" />
      </section>

      <div class="grid gap-5 lg:grid-cols-[minmax(0,1fr)_19rem] lg:items-start">
        <form class="fin-card space-y-5 p-4 sm:p-5" novalidate @submit.prevent="onContinue">
          <div
            v-if="formError"
            class="flex items-start gap-2 rounded-md bg-danger-light px-3 py-2.5 text-[0.875rem] text-danger-dark"
            role="alert"
          >
            <Info :size="16" class="mt-0.5 shrink-0" aria-hidden="true" />
            <span>{{ formError }}</span>
          </div>

          <!-- Step 1 — sender and recipient -->
          <fieldset class="space-y-4">
            <legend class="text-headline text-ink">Step 1 · Recipient</legend>

            <BaseSelect
              v-if="!useAccountCards"
              :model-value="senderAccountId"
              label="From"
              :options="senderOptions"
              placeholder="Choose an account"
              hint="Only active accounts can send money."
              @update:model-value="onSenderInput"
            />

            <fieldset v-else class="space-y-2">
              <legend class="fin-label">From</legend>
              <label
                v-for="account in activeAccounts"
                :key="account.id"
                class="flex cursor-pointer items-start gap-3 rounded-md border p-3 transition-colors"
                :class="
                  senderAccountId === account.id
                    ? 'border-primary bg-primary-soft'
                    : 'border-border bg-surface hover:border-border-strong'
                "
              >
                <input
                  v-model="senderAccountId"
                  type="radio"
                  name="sender-account"
                  :value="account.id"
                  class="mt-1 h-4 w-4 accent-primary"
                />
                <span class="min-w-0 flex-1">
                  <span class="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
                    <span class="truncate text-[0.9375rem] font-semibold text-ink">
                      {{ accountLabel(account) }}
                    </span>
                    <span class="fin-amount text-[0.9375rem] text-ink">
                      {{ formatAmount(account.availableBalance) }}
                      <span class="ml-1 text-caption font-semibold text-ink-muted">{{ account.currency }}</span>
                    </span>
                  </span>
                  <span class="mt-0.5 block font-mono text-caption text-ink-subtle">
                    {{ account.maskedAccountNumber }} ·
                    {{ account.accountType === 'SAVINGS' ? 'Savings' : 'Checking' }}
                  </span>
                </span>
              </label>
            </fieldset>

            <p v-if="sender" class="-mt-2 text-caption text-ink-subtle">
              Available {{ formatMoney(sender.availableBalance, sender.currency) }} ·
              {{ sender.accountType === 'SAVINGS' ? 'Savings account' : 'Checking account' }}
            </p>

            <div class="space-y-2.5">
              <BaseInput
                :model-value="recipientNumber"
                label="Recipient account number"
                placeholder="TN58 0000 0000 0000 0000 00"
                autocomplete="off"
                :maxlength="34"
                required
                :error="recipientError ?? undefined"
                :disabled="verifying"
                hint="Use the number printed on the recipient's Finova account or on a Finova transfer receipt."
                @update:model-value="onRecipientInput"
              >
                <template #suffix>
                  <button
                    type="button"
                    class="flex h-8 w-8 items-center justify-center rounded-md text-primary transition-colors hover:bg-primary-soft disabled:opacity-50"
                    aria-label="Verify recipient account"
                    :disabled="verifying"
                    @click="onVerify"
                  >
                    <Search :size="16" aria-hidden="true" />
                  </button>
                </template>
              </BaseInput>

              <BaseButton
                variant="secondary"
                size="sm"
                :loading="verifying"
                :disabled="verifying"
                @click="onVerify"
              >
                Verify recipient
              </BaseButton>

              <div
                v-if="recipient"
                class="rounded-md border border-border bg-surface-sunken p-3.5"
                role="status"
                aria-live="polite"
              >
                <div class="flex items-center gap-2.5">
                  <span
                    class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-success-light text-success"
                    aria-hidden="true"
                  >
                    <ShieldCheck :size="16" />
                  </span>
                  <div class="min-w-0">
                    <p class="text-[0.9375rem] font-semibold text-ink">Verified Finova account</p>
                    <p class="font-mono text-caption text-ink-muted">{{ recipient.maskedAccountNumber }}</p>
                  </div>
                </div>

                <dl class="mt-3 divide-y divide-border">
                  <DetailRow label="Account number" :value="recipient.maskedAccountNumber" mono />
                  <DetailRow
                    label="Account type"
                    :value="recipient.accountType === 'SAVINGS' ? 'Savings account' : 'Checking account'"
                  />
                  <DetailRow label="Currency" :value="recipient.currency" />
                  <DetailRow label="Bank" :value="recipient.bankName" />
                  <DetailRow
                    label="Status"
                    :value="recipient.status === 'ACTIVE' ? 'Active — can receive money' : recipient.status"
                  />
                </dl>

                <p class="mt-2 text-caption text-ink-subtle">
                  Finova never shows the recipient's full name. Confirm the last four digits with them before you send.
                </p>
              </div>
            </div>
          </fieldset>

          <hr class="border-border" />

          <!-- Step 2 — amount and description -->
          <fieldset class="space-y-4">
            <legend class="text-headline text-ink">Step 2 · Amount</legend>

            <BaseInput
              v-model="amountText"
              label="Amount"
              type="text"
              inputmode="decimal"
              autocomplete="off"
              placeholder="0.000"
              required
              :error="amountError ?? undefined"
              :hint="
                currencyMismatch
                  ? undefined
                  : `Between ${formatMoney(MIN_AMOUNT, sender?.currency ?? 'TND')} and ${formatMoney(MAX_AMOUNT, sender?.currency ?? 'TND')}.`
              "
            >
              <template #suffix>
                <span class="text-caption font-bold uppercase tracking-wide text-ink-muted">
                  {{ sender?.currency ?? 'TND' }}
                </span>
              </template>
            </BaseInput>

            <p
              v-if="currencyMismatch"
              class="flex items-start gap-2 rounded-md bg-warning-light px-3 py-2.5 text-caption text-warning-dark"
              role="alert"
            >
              <Info :size="15" class="mt-0.5 shrink-0" aria-hidden="true" />
              <span>
                Transfers are only possible between accounts of the same currency. This recipient holds
                {{ recipient?.currency }} — send from a {{ recipient?.currency }} account instead.
              </span>
            </p>

            <div
              v-if="amountValue !== null && remainingBalance !== null && !currencyMismatch"
              class="flex flex-wrap items-center justify-between gap-2 rounded-md bg-primary-soft px-3 py-2.5"
              aria-live="polite"
            >
              <span class="text-caption font-semibold uppercase tracking-wider text-primary">
                Balance after this transfer
              </span>
              <span
                class="fin-amount text-[0.9375rem]"
                :class="remainingBalance < 0 ? 'text-danger' : 'text-ink'"
              >
                {{ formatAmount(remainingBalance) }}
                <span class="ml-1 text-caption font-semibold text-ink-muted">{{ sender?.currency }}</span>
              </span>
            </div>

            <div>
              <BaseInput
                v-model="description"
                label="Description"
                type="text"
                :maxlength="MAX_DESCRIPTION"
                placeholder="e.g. Rent for October"
                hint="Appears on both statements so the recipient recognises the payment."
              />
              <p class="mt-1 text-right text-caption text-ink-subtle" aria-live="polite">
                {{ description.length }} / {{ MAX_DESCRIPTION }} characters
              </p>
            </div>
          </fieldset>

          <div class="flex flex-col-reverse gap-2 border-t border-border pt-4 sm:flex-row sm:justify-end">
            <BaseButton variant="ghost" to="/dashboard">Cancel</BaseButton>
            <BaseButton type="submit" :disabled="verifying">
              Continue to review
              <template #trailing>
                <ArrowRight :size="16" aria-hidden="true" />
              </template>
            </BaseButton>
          </div>
        </form>

        <!-- Live summary -->
        <aside class="space-y-4 lg:sticky lg:top-20">
          <section class="fin-card p-4 sm:p-5" aria-label="Transfer summary">
            <h2 class="text-headline text-ink">Summary</h2>
            <dl class="mt-2 divide-y divide-border">
              <DetailRow
                label="From"
                :value="sender ? `${accountLabel(sender)} · ${sender.maskedAccountNumber}` : undefined"
                hint="The account the money leaves"
              />
              <DetailRow
                label="To"
                :value="recipient ? recipient.maskedAccountNumber : recipientNumber.trim() || undefined"
                hint="Finova keeps the recipient's name hidden"
              />
              <DetailRow label="Amount" :value="amountSummary" emphasis />
              <DetailRow
                label="Fee"
                :value="formatMoney(0, sender?.currency ?? 'TND')"
                hint="Finova charges no transfer fee."
              />
              <DetailRow label="Total" :value="amountSummary" emphasis hint="Leaves your account today" />
            </dl>
          </section>

          <section class="rounded-lg border border-accent/25 bg-accent-light p-4">
            <p class="flex items-center gap-2 text-label text-accent-dark">
              <ShieldCheck :size="15" aria-hidden="true" />
              Fraud screening
            </p>
            <p class="mt-1.5 text-[0.875rem] leading-relaxed text-ink-muted">
              Every transfer is scored by our risk engine before settlement. Unusual transfers are held for review
              rather than silently dropped.
            </p>
          </section>
        </aside>
      </div>
    </template>
  </div>
</template>
