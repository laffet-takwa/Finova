<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight, Info, ShieldCheck, TriangleAlert } from 'lucide-vue-next'
import PageHeader from '@/components/ui/PageHeader.vue'
import StepProgress, { type StepProgressItem } from '@/components/ui/StepProgress.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import DetailRow from '@/components/ui/DetailRow.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import Skeleton from '@/components/ui/Skeleton.vue'
import { useAccountStore } from '@/stores/accountStore'
import { useTransactionStore, type TransferDraft } from '@/stores/transactionStore'
import { useToastStore } from '@/stores/toastStore'
import { AppError } from '@/utils/errors'
import { formatMoney } from '@/utils/format'
import type { Account, Currency } from '@/types'

const STEPS: StepProgressItem[] = [
  { label: 'Recipient' },
  { label: 'Amount' },
  { label: 'Review' },
  { label: 'Complete' },
]

const route = useRoute()
const router = useRouter()
const accountStore = useAccountStore()
const transactionStore = useTransactionStore()
const toast = useToastStore()

const loading = ref(true)
const loadError = ref<string | null>(null)
const loadOffline = ref(false)
const submitting = ref(false)
const submitError = ref<string | null>(null)
const submitCode = ref<string | null>(null)

const draft = ref<TransferDraft | null>(null)
const senderAccountId = ref('')
const receiverAccountNumber = ref('')
const amount = ref<number | null>(null)
const currency = ref<Currency>('TND')
const description = ref('')
const idempotencyKey = ref('')

const sender = computed<Account | null>(
  () => accountStore.byId[senderAccountId.value] ?? null,
)

const fee = computed(() => 0)
const total = computed(() => (amount.value ?? 0) + fee.value)
const hasDraft = computed(() => Boolean(draft.value && amount.value !== null && idempotencyKey.value))

/** Rebuilds the draft from the URL first (survives a refresh), the store second. */
function hydrate(): void {
  const query = route.query
  const fromQuery =
    typeof query.s === 'string' &&
    typeof query.r === 'string' &&
    typeof query.a === 'string' &&
    typeof query.k === 'string' &&
    Number.isFinite(Number(query.a))

  if (fromQuery) {
    senderAccountId.value = query.s as string
    receiverAccountNumber.value = (query.r as string).replace(/\s/g, '')
    amount.value = Number(query.a as string)
    currency.value = (typeof query.c === 'string' ? query.c : 'TND') as Currency
    description.value = typeof query.d === 'string' ? query.d : ''
    idempotencyKey.value = query.k as string
    draft.value = {
      senderAccountId: senderAccountId.value,
      receiverAccountNumber: receiverAccountNumber.value,
      amount: amount.value,
      currency: currency.value,
      description: description.value,
      idempotencyKey: idempotencyKey.value,
      recipient: transactionStore.transferDraft?.recipient ?? null,
      createdAt: new Date().toISOString(),
    }
    return
  }

  const stored = transactionStore.transferDraft
  if (stored) {
    senderAccountId.value = stored.senderAccountId
    receiverAccountNumber.value = stored.receiverAccountNumber
    amount.value = stored.amount
    currency.value = stored.currency
    description.value = stored.description
    idempotencyKey.value = stored.idempotencyKey
    draft.value = stored
  }
}

async function load(force = false): Promise<void> {
  loading.value = true
  loadError.value = null
  hydrate()
  // No draft means there is nothing to review — the empty state covers it and
  // there is no reason to hit the accounts endpoint.
  if (!draft.value) {
    loading.value = false
    return
  }
  try {
    await accountStore.fetchAll(force)
  } catch (cause) {
    loadError.value = cause instanceof Error ? cause.message : 'We could not load your accounts.'
    loadOffline.value = cause instanceof AppError ? cause.isNetworkError : false
  } finally {
    loading.value = false
  }
}

function maskedNumber(value: string): string {
  const clean = value.replace(/\s/g, '')
  return clean.length > 4 ? `•••• ${clean.slice(-4)}` : clean
}

async function onBack(): Promise<void> {
  await router.push({
    name: 'transfer',
    query: draft.value ? { account: draft.value.senderAccountId } : {},
  })
}

async function onConfirm(): Promise<void> {
  if (!draft.value || amount.value === null || submitting.value) return
  submitting.value = true
  submitError.value = null
  submitCode.value = null
  try {
    const created = await transactionStore.createTransfer(
      {
        senderAccountId: draft.value.senderAccountId,
        receiverAccountNumber: draft.value.receiverAccountNumber,
        amount: draft.value.amount,
        currency: draft.value.currency,
        description: draft.value.description || undefined,
      },
      draft.value.idempotencyKey,
    )

    if (transactionStore.lastIdempotentReplay) {
      toast.info(
        'Already submitted',
        `We matched this transfer to an earlier submission (${created.reference}). No second payment was made.`,
      )
    }

    transactionStore.clearTransferDraft()
    await router.push({ name: 'transfer-success', params: { id: created.id } })
  } catch (cause) {
    if (cause instanceof AppError) {
      submitError.value = cause.message
      submitCode.value = cause.code
    } else {
      submitError.value = 'We could not send this transfer. Please try again.'
    }
  } finally {
    submitting.value = false
  }
}

onMounted(() => void load())
</script>

<template>
  <div class="space-y-5 pb-4">
    <PageHeader
      eyebrow="Step 3 of 4"
      title="Review transfer"
      description="Check every detail. Once you confirm, this transfer is sent immediately and cannot be undone."
    />

    <div v-if="loading" class="fin-card space-y-4 p-5" aria-busy="true">
      <Skeleton variant="block" :rows="3" />
      <span class="sr-only">Loading your transfer</span>
    </div>

    <ErrorState
      v-else-if="loadError"
      class="fin-card"
      title="We could not load your accounts"
      :description="loadError"
      :offline="loadOffline"
      retry-label="Reload accounts"
      @retry="load(true)"
    >
      <BaseButton variant="ghost" size="sm" @click="onBack">Back to the transfer form</BaseButton>
    </ErrorState>

    <div v-else-if="!hasDraft" class="fin-card">
      <EmptyState
        title="Nothing to review yet"
        description="There is no transfer in progress. Start a new transfer and we will show you a summary before anything is sent."
        action-label="Start a transfer"
        @action="router.push({ name: 'transfer' })"
      />
    </div>

    <template v-else>
      <section class="fin-card p-4 sm:p-5">
        <StepProgress :steps="STEPS" :current="3" aria-label="Transfer progress" />
      </section>

      <div class="grid gap-5 lg:grid-cols-[minmax(0,1fr)_19rem] lg:items-start">
        <div class="space-y-5">
          <!-- The transfer summary -->
          <section class="fin-card overflow-hidden">
            <header class="border-b border-border bg-surface-sunken px-4 py-3.5 sm:px-5">
              <h2 class="text-headline text-ink">Transfer summary</h2>
              <p class="mt-0.5 text-caption text-ink-muted">
                Finova cannot recover a transfer once it has been sent.
              </p>
            </header>

            <div class="grid gap-0 sm:grid-cols-[1fr_auto_1fr] sm:items-center">
              <div class="p-4 sm:p-5">
                <p class="text-caption font-semibold uppercase tracking-wider text-ink-subtle">From</p>
                <p class="mt-1 text-[1.0625rem] font-semibold text-ink">
                  {{ sender?.nickname ?? 'Your account' }}
                </p>
                <p class="font-mono text-[0.8125rem] text-ink-muted">
                  {{ sender?.maskedAccountNumber ?? '—' }}
                </p>
                <p v-if="sender" class="mt-1.5 text-caption text-ink-subtle">
                  Available {{ formatMoney(sender.availableBalance, sender.currency) }}
                </p>
              </div>

              <div class="hidden px-2 text-ink-subtle sm:block" aria-hidden="true">
                <ArrowRight :size="22" />
              </div>

              <div class="border-t border-border p-4 sm:border-l sm:border-t-0 sm:p-5">
                <p class="text-caption font-semibold uppercase tracking-wider text-ink-subtle">To</p>
                <p class="mt-1 text-[1.0625rem] font-semibold text-ink">Finova account</p>
                <p class="font-mono text-[0.8125rem] text-ink-muted">
                  {{ draft?.recipient?.maskedAccountNumber ?? maskedNumber(receiverAccountNumber) }}
                </p>
                <p class="mt-1.5 text-caption text-ink-subtle">
                  {{ draft?.recipient?.bankName ?? 'Finova Bank' }} ·
                  {{ draft?.recipient?.accountType === 'SAVINGS' ? 'Savings account' : 'Checking account' }}
                  <template v-if="draft?.recipient?.status">
                    · {{ draft.recipient.status === 'ACTIVE' ? 'Active' : draft.recipient.status }}
                  </template>
                </p>
              </div>
            </div>

            <div class="border-t border-border px-4 py-2 sm:px-5">
              <dl class="divide-y divide-border">
                <DetailRow label="Amount" :value="formatMoney(amount ?? 0, currency)" emphasis />
                <DetailRow
                  label="Fee"
                  :value="formatMoney(fee, currency)"
                  hint="Finova charges no transfer fee — the whole amount goes to the recipient."
                />
                <DetailRow label="Total" :value="formatMoney(total, currency)" emphasis />
                <DetailRow v-if="description" label="Description" :value="description" />
              </dl>
            </div>

            <footer class="flex items-start gap-2 border-t border-border bg-primary-soft px-4 py-3 sm:px-5">
              <ShieldCheck :size="15" class="mt-0.5 shrink-0 text-primary" aria-hidden="true" />
              <p class="text-caption text-ink-muted">
                Transfers are screened by our risk engine before settlement. An unusual transfer is held for review and
                you are notified — it is never silently dropped.
              </p>
            </footer>
          </section>

          <!-- Inline failure -->
          <div
            v-if="submitError"
            class="rounded-lg border border-danger/25 bg-danger-light p-4"
            role="alert"
          >
            <p class="flex items-start gap-2 text-[0.9375rem] font-semibold text-danger-dark">
              <TriangleAlert :size="17" class="mt-0.5 shrink-0" aria-hidden="true" />
              This transfer was not sent
            </p>
            <p class="mt-1.5 text-[0.875rem] leading-relaxed text-ink-muted">{{ submitError }}</p>
            <p class="mt-1 text-caption text-ink-subtle">
              Nothing was debited from your account. You can go back, adjust the details and try again.
            </p>
            <div class="mt-3 flex flex-wrap gap-2">
              <BaseButton
                v-if="submitCode === 'INSUFFICIENT_BALANCE'"
                size="sm"
                variant="secondary"
                @click="onBack"
              >
                Back to amount
              </BaseButton>
              <BaseButton v-else size="sm" variant="secondary" @click="onBack">
                <template #icon>
                  <ArrowLeft :size="14" aria-hidden="true" />
                </template>
                Back to details
              </BaseButton>
            </div>
          </div>
        </div>

        <!-- Actions -->
        <aside class="space-y-4 lg:sticky lg:top-20">
          <section class="fin-card p-4 sm:p-5">
            <p class="text-label text-ink">Ready to send?</p>
            <p class="mt-1.5 text-[0.875rem] leading-relaxed text-ink-muted">
              Confirming debits {{ sender?.nickname ?? 'your account' }} immediately.
            </p>

            <div class="mt-4 space-y-2">
              <BaseButton
                variant="primary"
                size="lg"
                block
                :loading="submitting"
                :disabled="submitting"
                @click="onConfirm"
              >
                Confirm transfer
              </BaseButton>
              <BaseButton variant="secondary" block :disabled="submitting" @click="onBack">
                <template #icon>
                  <ArrowLeft :size="16" aria-hidden="true" />
                </template>
                Back
              </BaseButton>
            </div>

            <p class="mt-3 flex items-start gap-1.5 text-caption text-ink-subtle">
              <Info :size="13" class="mt-0.5 shrink-0" aria-hidden="true" />
              <span>
                Safe to retry: this transfer carries one idempotency key, so a repeated submission can never debit you
                twice.
              </span>
            </p>
          </section>
        </aside>
      </div>
    </template>
  </div>
</template>
