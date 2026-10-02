<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowUpRight, Receipt } from 'lucide-vue-next'
import PageHeader from '@/components/ui/PageHeader.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseInput from '@/components/ui/BaseInput.vue'
import BaseSelect from '@/components/ui/BaseSelect.vue'
import FilterBar from '@/components/ui/FilterBar.vue'
import SearchInput from '@/components/ui/SearchInput.vue'
import DateRangePicker from '@/components/ui/DateRangePicker.vue'
import Skeleton from '@/components/ui/Skeleton.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import TransactionTable from '@/components/transaction/TransactionTable.vue'
import ChartCard from '@/components/charts/ChartCard.vue'
import { useAccountStore } from '@/stores/accountStore'
import { useTransactionStore } from '@/stores/transactionStore'
import { formatMoney } from '@/utils/format'
import type { Currency, Transaction, TransactionFilters, TransactionStatus, TransactionType } from '@/types'

const route = useRoute()
const router = useRouter()
const accountStore = useAccountStore()
const transactionStore = useTransactionStore()

const TYPE_OPTIONS = [
  { value: 'TRANSFER', label: 'Transfers' },
  { value: 'DEPOSIT', label: 'Deposits' },
  { value: 'WITHDRAWAL', label: 'Withdrawals' },
]

const STATUS_OPTIONS = [
  { value: 'COMPLETED', label: 'Completed' },
  { value: 'PENDING', label: 'Pending' },
  { value: 'PROCESSING', label: 'Processing' },
  { value: 'FLAGGED', label: 'Flagged' },
  { value: 'FAILED', label: 'Failed' },
  { value: 'REJECTED', label: 'Rejected' },
]

const ACCOUNT_TYPE_OPTIONS: Array<{ value: AccountType; label: string }> = [
  { value: 'CHECKING', label: 'Checking' },
  { value: 'SAVINGS', label: 'Savings' },
]

const searchText = ref(typeof route.query.q === 'string' ? route.query.q : '')

const filters = reactive({
  search: searchText.value,
  type: '' as TransactionType | '',
  status: '' as TransactionStatus | '',
  accountId: typeof route.query.account === 'string' ? route.query.account : '',
  currency: '' as Currency | '',
  minAmount: '',
  maxAmount: '',
  from: '',
  to: '',
})

const page = ref(0)
const pageSize = ref(20)
const listError = ref<string | null>(null)

const accountOptions = computed(() =>
  accountStore.accounts.map((account) => ({
    value: account.id,
    label: `${account.nickname ?? 'Account'} · ${account.maskedAccountNumber}`,
  })),
)

const currencyOptions = computed(() =>
  (['TND', 'EUR', 'USD'] as Currency[]).map((code) => ({ value: code, label: code })),
)

const activeFilterCount = computed(
  () =>
    [
      filters.search,
      filters.type,
      filters.status,
      filters.accountId,
      filters.currency,
      filters.minAmount,
      filters.maxAmount,
      filters.from,
      filters.to,
    ].filter((value) => String(value).trim().length > 0).length,
)

const loading = computed(() => transactionStore.loading)
const transactions = computed(() => transactionStore.transactions)

function buildQuery(targetPage = page.value): TransactionFilters {
  const parseAmount = (value: string): number | undefined => {
    const cleaned = value.replace(/\s/g, '').replace(',', '.').replace(/[^\d.]/g, '')
    if (cleaned.length === 0) return undefined
    const parsed = Number(cleaned)
    return Number.isFinite(parsed) ? parsed : undefined
  }

  return {
    search: filters.search || undefined,
    type: filters.type || undefined,
    status: filters.status || undefined,
    accountId: filters.accountId || undefined,
    currency: filters.currency || undefined,
    minAmount: parseAmount(filters.minAmount),
    maxAmount: parseAmount(filters.maxAmount),
    from: filters.from ? new Date(`${filters.from}T00:00:00`).toISOString() : undefined,
    to: filters.to ? new Date(`${filters.to}T23:59:59`).toISOString() : undefined,
    page: targetPage,
    size: pageSize.value,
    sort: 'createdAt,desc',
  }
}

async function load(): Promise<void> {
  listError.value = null
  try {
    await transactionStore.fetchList(buildQuery(), true)
  } catch (cause) {
    listError.value = cause instanceof Error ? cause.message : 'We could not load your transactions.'
  }
}

/** Any filter change resets to the first page and refetches. */
function reload(): void {
  page.value = 0
  void load()
}

function onSearch(value: string): void {
  if (filters.search === value) return
  filters.search = value
  reload()
}

function onReset(): void {
  searchText.value = ''
  filters.search = ''
  filters.type = ''
  filters.status = ''
  filters.accountId = ''
  filters.currency = ''
  filters.minAmount = ''
  filters.maxAmount = ''
  filters.from = ''
  filters.to = ''
  reload()
}

function onPageChange(next: number): void {
  page.value = next
  void load()
}

function onSizeChange(next: number): void {
  pageSize.value = next
  page.value = 0
  void load()
}

function onSelect(transaction: Transaction): void {
  void router.push({ name: 'transaction-detail', params: { id: transaction.id } })
}

watch(
  () => [filters.type, filters.status, filters.accountId, filters.currency, filters.minAmount, filters.maxAmount],
  () => reload(),
)
watch(() => [filters.from, filters.to], () => reload())

const summary = computed(() => transactionStore.summary)
const chartLabels = computed(() =>
  (summary.value?.dailySeries ?? []).slice(-14).map((point) => point.label),
)
const chartIncome = computed(() => (summary.value?.dailySeries ?? []).slice(-14).map((point) => point.income))
const chartExpenses = computed(() => (summary.value?.dailySeries ?? []).slice(-14).map((point) => point.expenses))

onMounted(async () => {
  try {
    await accountStore.fetchAll()
  } catch {
    // The account filter is a convenience; the list still loads without it.
  }
  await Promise.all([load(), transactionStore.fetchSummary().catch(() => null)])
})
</script>

<template>
  <div class="space-y-5 pb-4">
    <PageHeader
      eyebrow="Activity"
      title="Transactions"
      description="Every transfer, deposit and withdrawal across your accounts."
    >
      <template #actions>
        <BaseButton to="/transfer">
          <template #icon>
            <ArrowUpRight :size="16" aria-hidden="true" />
          </template>
          Send money
        </BaseButton>
      </template>
    </PageHeader>

    <!-- Loading the very first page: nothing to filter yet -->
    <div v-if="loading && transactions.length === 0 && !listError" class="fin-card p-5" aria-busy="true">
      <Skeleton variant="block" :rows="3" />
      <span class="sr-only">Loading your transactions</span>
    </div>

    <ErrorState
      v-else-if="listError && transactions.length === 0"
      class="fin-card"
      title="We could not load your transactions"
      :description="listError"
      retry-label="Reload transactions"
      @retry="load"
    />

    <template v-else>
      <FilterBar :active-count="activeFilterCount" @reset="onReset">
        <template #search>
          <SearchInput
            v-model="searchText"
            label="transactions"
            placeholder="Search description or reference"
            debounce-ms="350"
            @search="onSearch"
          />
        </template>

        <template #primary>
          <DateRangePicker
            v-model:from="filters.from"
            v-model:to="filters.to"
            label="Date range"
          />
        </template>

        <template #advanced>
          <BaseSelect
            v-model="filters.type"
            label="Type"
            :options="TYPE_OPTIONS"
            placeholder="All types"
          />
          <BaseSelect
            v-model="filters.status"
            label="Status"
            :options="STATUS_OPTIONS"
            placeholder="All statuses"
          />
          <BaseSelect
            v-model="filters.accountId"
            label="Account"
            :options="accountOptions"
            placeholder="All accounts"
          />
          <BaseSelect
            v-model="filters.currency"
            label="Currency"
            :options="currencyOptions"
            placeholder="All currencies"
          />
          <BaseInput
            v-model="filters.minAmount"
            label="Minimum amount"
            type="text"
            inputmode="decimal"
            placeholder="0.000"
          />
          <BaseInput
            v-model="filters.maxAmount"
            label="Maximum amount"
            type="text"
            inputmode="decimal"
            placeholder="No limit"
          />
        </template>
      </FilterBar>

      <div
        v-if="listError"
        class="rounded-md bg-danger-light px-3 py-2.5 text-[0.875rem] text-danger-dark"
        role="alert"
      >
        {{ listError }}
        <button type="button" class="ml-2 font-semibold underline" @click="load">Try again</button>
      </div>

      <!-- Daily volume -->
      <ChartCard
        type="bar"
        title="Daily volume"
        subtitle="Money in and out over the last 14 days"
        :labels="chartLabels"
        :datasets="[
          { label: 'Money in', data: chartIncome, color: '#16A34A' },
          { label: 'Money out', data: chartExpenses, color: '#3B82F6' },
        ]"
        :currency="summary?.currency ?? 'TND'"
        :height="220"
        :loading="!summary"
        empty-message="No movement recorded in this period yet."
      >
        <template #actions>
          <p class="text-caption text-ink-subtle">
            In {{ formatMoney(summary?.income ?? 0, summary?.currency ?? 'TND') }} ·
            out {{ formatMoney(summary?.expenses ?? 0, summary?.currency ?? 'TND') }}
          </p>
        </template>
      </ChartCard>

      <div class="flex flex-wrap items-baseline justify-between gap-2">
        <h2 class="text-headline text-ink">
          <span aria-live="polite">{{ transactionStore.totalElements.toLocaleString('en-US') }}</span>
          transactions
        </h2>
        <p v-if="summary" class="text-caption text-ink-subtle">
          {{ summary.transactionCount }} movements recorded in total
        </p>
      </div>

      <TransactionTable
        :transactions="transactions"
        :loading="loading"
        :page="transactionStore.currentPage"
        :total-pages="transactionStore.totalPages"
        :total-elements="transactionStore.totalElements"
        :page-size="pageSize"
        show-risk
        empty-title="No transactions yet."
        empty-description="Your transactions will appear here once you make your first transfer."
        @select="onSelect"
        @page-change="onPageChange"
        @size-change="onSizeChange"
        @empty-action="router.push({ name: 'transfer' })"
      />
    </template>
  </div>
</template>
