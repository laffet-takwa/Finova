<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, TriangleAlert, Wallet } from 'lucide-vue-next'
import AccountCard from '@/components/account/AccountCard.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseInput from '@/components/ui/BaseInput.vue'
import BaseModal from '@/components/ui/BaseModal.vue'
import BaseSelect from '@/components/ui/BaseSelect.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import Skeleton from '@/components/ui/Skeleton.vue'
import { useAccountStore } from '@/stores/accountStore'
import { useToastStore } from '@/stores/toastStore'
import { AppError } from '@/utils/errors'
import { CURRENCIES, formatAmount, formatMoney } from '@/utils/format'
import type { AccountType, Currency } from '@/types'

const ACCOUNT_LIMIT = 3

const CURRENCY_NAMES: Record<Currency, string> = {
  TND: 'Tunisian dinar',
  EUR: 'Euro',
  USD: 'US dollar',
}

const router = useRouter()
const accountStore = useAccountStore()
const toast = useToastStore()

const loading = ref(true)
const error = ref('')
const modalOpen = ref(false)
const submitting = ref(false)
const modalError = ref('')

const accountType = ref<AccountType | ''>('')
const currency = ref<Currency | ''>('')
const nickname = ref('')
const openingBalance = ref('')

const accountTypeOptions = [
  { value: 'CHECKING', label: 'Checking — everyday spending' },
  { value: 'SAVINGS', label: 'Savings — money you set aside' },
]

const currencyOptions = CURRENCIES.map((code) => ({ value: code, label: `${code} — ${CURRENCY_NAMES[code]}` }))

const currencyTotals = computed(() =>
  Object.entries(accountStore.totalsByCurrency).sort(([left], [right]) => left.localeCompare(right)),
)
const singleCurrencyTotal = computed(() => {
  const [entry] = currencyTotals.value
  if (!entry) return null
  return { currency: entry[0], total: entry[1] }
})
const atLimit = computed(() => accountStore.accounts.length >= ACCOUNT_LIMIT)
const duplicateCombination = computed(
  () =>
    Boolean(accountType.value) &&
    Boolean(currency.value) &&
    accountStore.accounts.some(
      (account) => account.accountType === accountType.value && account.currency === currency.value,
    ),
)

function messageOf(cause: unknown, fallback: string): string {
  return cause instanceof Error && cause.message ? cause.message : fallback
}

async function load(force = false): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    await accountStore.fetchAll(force)
  } catch (cause) {
    error.value = messageOf(cause, 'We could not load your accounts.')
  } finally {
    loading.value = false
  }
}

function openModal(): void {
  accountType.value = ''
  currency.value = ''
  nickname.value = ''
  openingBalance.value = ''
  modalError.value = ''
  modalOpen.value = true
}

function closeModal(): void {
  if (submitting.value) return
  modalOpen.value = false
}

async function submitCreate(): Promise<void> {
  if (submitting.value) return
  const type = accountType.value
  const selectedCurrency = currency.value
  modalError.value = ''

  if (!type || !selectedCurrency) {
    modalError.value = 'Choose an account type and a currency.'
    return
  }

  const rawBalance = openingBalance.value.trim()
  const parsedBalance = rawBalance === '' ? undefined : Number(rawBalance.replace(',', '.'))
  if (parsedBalance !== undefined && (!Number.isFinite(parsedBalance) || parsedBalance < 0)) {
    modalError.value = 'Enter the opening balance as a positive number, or leave it empty.'
    return
  }

  submitting.value = true
  try {
    const created = await accountStore.create({
      accountType: type,
      currency: selectedCurrency,
      nickname: nickname.value.trim() || undefined,
      openingBalance: parsedBalance,
    })
    modalOpen.value = false
    toast.success(
      'Account opened',
      `${created.maskedAccountNumber} is active and ready to use.`,
    )
    await accountStore.fetchAll(true).catch(() => undefined)
  } catch (cause) {
    modalError.value =
      cause instanceof AppError ? cause.message : 'We could not open the account. Please try again.'
  } finally {
    submitting.value = false
  }
}

function goToAccount(accountId: string): void {
  void router.push({ name: 'account-detail', params: { id: accountId } })
}

function goToTransfer(accountId: string): void {
  void router.push({ name: 'transfer', query: { account: accountId } })
}

onMounted(() => void load())
</script>

<template>
  <div class="space-y-5">
    <PageHeader
      eyebrow="Accounts"
      title="My accounts"
      description="Everyday spending, savings and the balances you can send from."
    >
      <template #actions>
        <BaseButton @click="openModal">
          <template #icon>
            <Plus :size="16" aria-hidden="true" />
          </template>
          Open new account
        </BaseButton>
      </template>
    </PageHeader>

    <section
      v-if="accountStore.accounts.length && !error"
      class="fin-card p-4 sm:p-5"
      aria-labelledby="accounts-summary-heading"
    >
      <h2 id="accounts-summary-heading" class="sr-only">Balance summary</h2>

      <div v-if="loading" class="space-y-3">
        <Skeleton width="9rem" />
        <Skeleton width="16rem" />
      </div>

      <template v-else-if="singleCurrencyTotal">
        <p class="text-caption font-semibold uppercase tracking-wider text-ink-subtle">
          Total across {{ accountStore.activeAccounts.length }}
          {{ accountStore.activeAccounts.length === 1 ? 'active account' : 'active accounts' }}
        </p>
        <p class="fin-amount mt-1 text-money text-ink">
          {{ formatAmount(accountStore.totalBalance) }}
          <span class="ml-1 text-base font-semibold text-ink-muted">{{ singleCurrencyTotal.currency }}</span>
        </p>
      </template>

      <template v-else>
        <p class="text-caption font-semibold uppercase tracking-wider text-ink-subtle">
          Balances by currency
        </p>
        <dl class="mt-2 grid gap-3 sm:grid-cols-3">
          <div
            v-for="[code, total] in currencyTotals"
            :key="code"
            class="rounded-md border border-border bg-surface-sunken px-3.5 py-3"
          >
            <dt class="text-caption text-ink-subtle">{{ CURRENCY_NAMES[code as Currency] ?? code }}</dt>
            <dd class="fin-amount mt-0.5 text-[1.125rem] text-ink">{{ formatMoney(total, code) }}</dd>
          </div>
        </dl>
        <p class="mt-2.5 text-caption text-ink-subtle">
          Each currency is kept separate — Finova never converts your balances for you.
        </p>
      </template>
    </section>

    <div v-if="loading" class="grid gap-4 sm:gap-5 sm:grid-cols-2 xl:grid-cols-3">
      <div v-for="placeholder in 3" :key="placeholder" class="fin-card p-5">
        <Skeleton variant="block" :rows="4" />
      </div>
    </div>

    <div v-else-if="error" class="fin-card">
      <ErrorState title="We could not load your accounts" :description="error" @retry="load(true)" />
    </div>

    <div v-else-if="!accountStore.accounts.length" class="fin-card">
      <EmptyState
        title="No accounts yet"
        description="Open your first Finova account to send money, receive salary and keep an eye on every dinar."
        :icon="Wallet"
        action-label="Open your first account"
        @action="openModal"
      />
    </div>

    <ul v-else class="grid gap-4 sm:gap-5 sm:grid-cols-2 xl:grid-cols-3">
      <li v-for="account in accountStore.accounts" :key="account.id">
        <AccountCard
          :account="account"
          class="h-full"
          @view="goToAccount(account.id)"
          @transfer="goToTransfer(account.id)"
        />
      </li>
    </ul>

    <BaseModal
      :open="modalOpen"
      title="Open a new account"
      description="You can hold up to three accounts — one checking and one savings account per currency."
      size="md"
      @close="closeModal"
    >
      <form id="open-account-form" class="space-y-4" novalidate @submit.prevent="submitCreate">
        <BaseSelect
          v-model="accountType"
          label="Account type"
          :options="accountTypeOptions"
          placeholder="Choose an account type"
          required
        />

        <BaseSelect
          v-model="currency"
          label="Currency"
          :options="currencyOptions"
          placeholder="Choose a currency"
          required
        />

        <BaseInput
          v-model="nickname"
          label="Nickname"
          placeholder="Everyday spending"
          :maxlength="40"
        />

        <BaseInput
          v-model="openingBalance"
          label="Opening balance"
          type="number"
          inputmode="decimal"
          min="0"
          step="0.001"
          placeholder="0.000"
          hint="Leave empty and Finova opens the account at zero."
        />

        <p
          v-if="duplicateCombination"
          class="flex items-start gap-2 rounded-md border border-warning/30 bg-warning-light px-3.5 py-2.5 text-[0.8125rem] leading-relaxed text-warning-dark"
          role="status"
        >
          <TriangleAlert :size="15" class="mt-0.5 shrink-0" aria-hidden="true" />
          You already hold a {{ accountType?.toLowerCase() }} account in {{ currency }}. Pick another
          combination.
        </p>

        <p
          v-if="atLimit"
          class="flex items-start gap-2 rounded-md border border-warning/30 bg-warning-light px-3.5 py-2.5 text-[0.8125rem] leading-relaxed text-warning-dark"
          role="status"
        >
          <TriangleAlert :size="15" class="mt-0.5 shrink-0" aria-hidden="true" />
          You already hold {{ accountStore.accounts.length }} accounts, which is the Finova maximum.
        </p>

        <div
          v-if="modalError"
          role="alert"
          class="flex items-start gap-2.5 rounded-md border border-danger/30 bg-danger-light px-3.5 py-3"
        >
          <TriangleAlert :size="17" class="mt-0.5 shrink-0 text-danger" aria-hidden="true" />
          <p class="text-[0.875rem] font-medium leading-relaxed text-danger-dark">{{ modalError }}</p>
        </div>
      </form>

      <template #footer>
        <BaseButton variant="secondary" :disabled="submitting" @click="closeModal">Cancel</BaseButton>
        <BaseButton type="submit" form="open-account-form" :loading="submitting" :disabled="submitting">
          Open account
        </BaseButton>
      </template>
    </BaseModal>
  </div>
</template>