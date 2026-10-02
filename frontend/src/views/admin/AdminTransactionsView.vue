<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Eye, Search, ShieldQuestion, X } from 'lucide-vue-next'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseInput from '@/components/ui/BaseInput.vue'
import BaseSelect from '@/components/ui/BaseSelect.vue'
import DateRangePicker from '@/components/ui/DateRangePicker.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import FilterBar from '@/components/ui/FilterBar.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import SearchInput from '@/components/ui/SearchInput.vue'
import Skeleton from '@/components/ui/Skeleton.vue'
import TransactionTable from '@/components/transaction/TransactionTable.vue'
import { fraudApi } from '@/api/fraudApi'
import { useAdminStore } from '@/stores/adminStore'
import { useToastStore } from '@/stores/toastStore'
import { AppError } from '@/utils/errors'
import { maskAccountNumber } from '@/utils/format'
import type { Account, Currency, Transaction, TransactionFilters, TransactionStatus, TransactionType } from '@/types'

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

const CURRENCY_OPTIONS = [
  { value: 'TND', label: 'TND — Tunisian dinar' },
  { value: 'EUR', label: 'EUR — Euro' },
  { value: 'USD', label: 'USD — US dollar' },
]

const route = useRoute()
const router = useRouter()
const adminStore = useAdminStore()
const toast = useToastStore()

const searchText = ref(typeof route.query.q === 'string' ? route.query.q : '')

const filters = reactive({
  search: searchText.value,
  type: '' as TransactionType | '',
  status: '' as TransactionStatus | '',
  currency: '' as Currency | '',
  accountId: typeof route.query.account === 'string' ? route.query.account : '',
  minAmount: '',
  maxAmount: '',
  from: '',
  to: '',
})

const page = ref(0)
const pageSize = ref(20)
const listError = ref<string | null>(null)
const reviewingId = ref<string | null>(null)

const transactions = computed(() => adminStore.transactions?.content ?? [])
const totalElements = computed(() => adminStore.transactions?.totalElements ?? 0)
const totalPages = computed(() => adminStore.transactions?.totalPages ?? 0)
const loading = computed(() => adminStore.loading)
const accountOptions = computed<Account[]>(() => adminStore.accounts?.content ?? [])

const accountFilterOptions = computed(() =>
  accountOptions.value.map((account) => ({
    value: account.id,
    label: `${account.nickname || 'Account'} · ${maskAccountNumber(account.accountNumber)}`,
  })),
)

interface ActiveChip {
  key: string
  label: string
  clear: () => void
}

const activeChips = computed<ActiveChip[]>(() => {
  const chips: ActiveChip[] = []
  if (filters.search) {
    chips.push({ key: 'search', label: `Search “${filters.search}”`, clear: () => {
      filters.search = ''
      searchText.value = ''
    } })
  }
  if (filters.type) {
    chips.push({ key: 'type', label: `Type: ${filters.type.toLowerCase()}`, clear: () => { filters.type = '' } })
  }
  if (filters.status) {
    chips.push({ key: 'status', label: `Status: ${filters.status.toLowerCase()}`, clear: () => { filters.status = '' } })
  }
  if (filters.currency) {
    chips.push({ key: 'currency', label: `Currency: ${filters.currency}`, clear: () => { filters.currency = '' } })
  }
  if (filters.accountId) {
    const account = accountOptions.value.find((item) => item.id === filters.accountId)
    chips.push({
      key: 'account',
      label: `Account: ${account ? account.nickname || maskAccountNumber(account.accountNumber) : filters.accountId}`,
      clear: () => { filters.accountId = '' },
    })
  }
  if (filters.minAmount) {
    chips.push({ key: 'min', label: `From ${filters.minAmount}`, clear: () => { filters.minAmount = '' } })
  }
  if (filters.maxAmount) {
    chips.push({ key: 'max', label: `Up to ${filters.maxAmount}`, clear: () => { filters.maxAmount = '' } })
  }
  if (filters.from) chips.push({ key: 'from', label: `From ${filters.from}`, clear: () => { filters.from = '' } })
  if (filters.to) chips.push({ key: 'to', label: `To ${filters.to}`, clear: () => { filters.to = '' } })
  return chips
})

const activeFilterCount = computed(() => activeChips.value.length)

function parseAmount(value: string): number | undefined {
  const cleaned = value.replace(/\s/g, '').replace(',', '.').replace(/[^\d.]/g, '')
  if (cleaned.length === 0) return undefined
  const parsed = Number(cleaned)
  return Number.isFinite(parsed) ? parsed : undefined
}

function buildQuery(targetPage = page.value): TransactionFilters {
  return {
    search: filters.search || undefined,
    type: filters.type || undefined,
    status: filters.status || undefined,
    currency: filters.currency || undefined,
    accountId: filters.accountId || undefined,
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
    await adminStore.fetchTransactions(buildQuery(), true)
  } catch (cause) {
    listError.value = cause instanceof Error ? cause.message : 'We could not load transactions.'
  }
}

/** Every filter change returns to the first page — page 4 of a new result set is meaningless. */
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
  filters.currency = ''
  filters.accountId = ''
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

function viewTransaction(transaction: Transaction): void {
  void router.push({ name: 'transaction-detail', params: { id: transaction.id } })
}

/**
 * Jumps to the fraud alert raised for this transfer. When the transaction was never
 * scored above the alert threshold there is nothing to open, and saying so is more
 * useful than a 404.
 */
async function reviewTransaction(transaction: Transaction): Promise<void> {
  if (reviewingId.value) return
  reviewingId.value = transaction.id
  try {
    const alert = await fraudApi.byTransaction(transaction.id)
    void router.push({ name: 'admin-fraud-detail', params: { id: alert.id } })
  } catch (cause) {
    if (cause instanceof AppError && cause.status === 404) {
      toast.info(
        'No fraud alert on this transfer',
        `${transaction.reference} was scored by the fraud service but never crossed the alert threshold, so there is nothing to review.`,
      )
    } else {
      toast.fromError(cause, 'We could not look up the fraud alert for this transfer')
    }
  } finally {
    reviewingId.value = null
  }
}

watch(
  () => [filters.type, filters.status, filters.currency, filters.accountId, filters.minAmount, filters.maxAmount],
  () => reload(),
)
watch(() => [filters.from, filters.to], () => reload())

onMounted(() => {
  void load()
  if (filters.accountId) {
    // The account picker needs the register; a failure only costs the dropdown.
    void adminStore.fetchAccounts({ page: 0, size: 50, sort: 'createdAt,desc' }, true).catch(() => null)
  }
})
</script>

<template>
  <div class="space-y-5 pb-4">
    <PageHeader
      eyebrow="Finova Administration"
      title="Transactions"
      description="Every movement recorded across the platform, whoever it belongs to."
    >
      <template #actions>
        <p class="text-caption text-ink-subtle">
          <span aria-live="polite" class="font-semibold text-ink">
            {{ totalElements.toLocaleString('en-US') }}
          </span>
          matching transaction{{ totalElements === 1 ? '' : 's' }}
        </p>
      </template>
    </PageHeader>

    <div v-if="loading && transactions.length === 0 && !listError" class="fin-card p-5" aria-busy="true">
      <Skeleton variant="block" :rows="4" />
      <span class="sr-only">Loading platform transactions</span>
    </div>

    <ErrorState
      v-else-if="listError && transactions.length === 0"
      class="fin-card"
      title="We could not load platform transactions"
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
            placeholder="Search reference or description"
            :debounce-ms="400"
            @search="onSearch"
          />
        </template>

        <template #primary>
          <DateRangePicker v-model:from="filters.from" v-model:to="filters.to" label="Date range" />
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
            v-model="filters.currency"
            label="Currency"
            :options="CURRENCY_OPTIONS"
            placeholder="All currencies"
          />
          <BaseSelect
            v-model="filters.accountId"
            label="Account"
            :options="accountFilterOptions"
            placeholder="Any account"
            hint="Either side of the transfer counts."
          />
          <BaseInput
            v-model="filters.minAmount"
            label="Minimum amount"
            inputmode="decimal"
            placeholder="0.000"
          />
          <BaseInput
            v-model="filters.maxAmount"
            label="Maximum amount"
            inputmode="decimal"
            placeholder="No limit"
          />
        </template>
      </FilterBar>

      <div v-if="activeChips.length" class="flex flex-wrap items-center gap-2">
        <span class="text-caption font-semibold uppercase tracking-wider text-ink-subtle">
          Filtering by
        </span>
        <button
          v-for="chip in activeChips"
          :key="chip.key"
          type="button"
          class="fin-chip border border-border bg-surface text-ink-muted transition-colors hover:border-border-strong hover:text-danger"
          :aria-label="`Remove filter: ${chip.label}`"
          @click="chip.clear()"
        >
          <span>{{ chip.label }}</span>
          <X :size="13" aria-hidden="true" />
        </button>
      </div>

      <div
        v-if="listError"
        class="rounded-md bg-danger-light px-3 py-2.5 text-[0.875rem] text-danger-dark"
        role="alert"
      >
        {{ listError }}
        <button type="button" class="ml-2 font-semibold underline" @click="load">Try again</button>
      </div>

      <TransactionTable
        :transactions="transactions"
        :loading="loading"
        :page="page"
        :total-pages="totalPages"
        :total-elements="totalElements"
        :page-size="pageSize"
        show-risk
        show-sender
        empty-title="No transactions match these filters"
        empty-description="Nothing on the platform matches the current search, status, type, currency, amount range or date window. Reset the filters to see all movement."
        empty-action-label="Reset filters"
        @select="viewTransaction"
        @page-change="onPageChange"
        @size-change="onSizeChange"
        @empty-action="onReset"
      >
        <template #rowActions="{ transaction }">
          <BaseButton
            variant="ghost"
            size="sm"
            :aria-label="`Open transaction ${transaction.reference}`"
            @click="viewTransaction(transaction)"
          >
            <template #icon>
              <Eye :size="14" aria-hidden="true" />
            </template>
            View
          </BaseButton>
          <BaseButton
            variant="secondary"
            size="sm"
            :loading="reviewingId === transaction.id"
            :disabled="reviewingId !== null"
            :aria-label="`Review fraud on transaction ${transaction.reference}`"
            @click="reviewTransaction(transaction)"
          >
            <template #icon>
              <ShieldQuestion :size="14" aria-hidden="true" />
            </template>
            Review
          </BaseButton>
        </template>
      </TransactionTable>

      <p class="flex items-start gap-2 text-caption text-ink-subtle">
        <Search :size="13" class="mt-0.5 shrink-0" aria-hidden="true" />
        <span>
          The <strong class="font-semibold text-ink-muted">User</strong> column shows the account the money
          left. The transaction contract carries no owner identity, so no customer name is available here.
        </span>
      </p>
    </template>
  </div>
</template>