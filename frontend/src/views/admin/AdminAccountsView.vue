<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Ban, CheckCircle2, Lock, Wallet, XCircle } from 'lucide-vue-next'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseInput from '@/components/ui/BaseInput.vue'
import BaseModal from '@/components/ui/BaseModal.vue'
import BaseSelect from '@/components/ui/BaseSelect.vue'
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue'
import CopyButton from '@/components/ui/CopyButton.vue'
import DetailRow from '@/components/ui/DetailRow.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import FilterBar from '@/components/ui/FilterBar.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import Pagination from '@/components/ui/Pagination.vue'
import SearchInput from '@/components/ui/SearchInput.vue'
import Skeleton from '@/components/ui/Skeleton.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import { useAdminStore } from '@/stores/adminStore'
import { useToastStore } from '@/stores/toastStore'
import { formatDateTime, formatMoney, maskAccountNumber } from '@/utils/format'
import type { Account, AccountFilters, AccountStatus, AccountType, Currency } from '@/types'

const STATUS_OPTIONS = [
  { value: 'ACTIVE', label: 'Active' },
  { value: 'BLOCKED', label: 'Blocked' },
  { value: 'CLOSED', label: 'Closed' },
]

const TYPE_OPTIONS = [
  { value: 'CHECKING', label: 'Checking' },
  { value: 'SAVINGS', label: 'Savings' },
]

const CURRENCY_OPTIONS = [
  { value: 'TND', label: 'TND — Tunisian dinar' },
  { value: 'EUR', label: 'EUR — Euro' },
  { value: 'USD', label: 'USD — US dollar' },
]

type Action = 'block' | 'activate' | 'close'

const route = useRoute()
const adminStore = useAdminStore()
const toast = useToastStore()

const searchText = ref('')
const filters = reactive({
  search: '',
  status: '' as AccountStatus | '',
  accountType: '' as AccountType | '',
  currency: '' as Currency | '',
  minBalance: '',
  maxBalance: '',
  userId: typeof route.query.user === 'string' ? route.query.user : '',
})

const page = ref(0)
const pageSize = ref(20)
const listError = ref<string | null>(null)

const selectedAccount = ref<Account | null>(null)
const detailOpen = ref(false)

const confirmOpen = ref(false)
const confirmAction = ref<Action>('block')
const reason = ref('')

const accounts = computed(() => adminStore.accounts?.content ?? [])
const totalElements = computed(() => adminStore.accounts?.totalElements ?? 0)
const totalPages = computed(() => adminStore.accounts?.totalPages ?? 0)
const loading = computed(() => adminStore.loading)
const acting = computed(() => adminStore.acting)
const stats = computed(() => adminStore.accountStats)

const activeFilterCount = computed(
  () =>
    [
      filters.search,
      filters.status,
      filters.accountType,
      filters.currency,
      filters.minBalance,
      filters.maxBalance,
      filters.userId,
    ].filter((value) => String(value).trim().length > 0).length,
)

const currencyTotals = computed(() => {
  const totals = stats.value?.totalBalanceByCurrency ?? {}
  return (Object.keys(totals) as string[]).map((code) => ({ code, total: totals[code] ?? 0 }))
})

const typeTotals = computed(() => {
  const totals = stats.value?.accountsByType ?? {}
  return (Object.keys(totals) as string[]).map((code) => ({ code, count: totals[code] ?? 0 }))
})

function shortId(value: string): string {
  return value.length <= 10 ? value : `${value.slice(0, 8)}…`
}

function parseAmount(value: string): number | undefined {
  const cleaned = value.replace(/\s/g, '').replace(',', '.').replace(/[^\d.]/g, '')
  if (cleaned.length === 0) return undefined
  const parsed = Number(cleaned)
  return Number.isFinite(parsed) ? parsed : undefined
}

function buildQuery(targetPage = page.value): AccountFilters {
  return {
    search: filters.search || undefined,
    status: filters.status || undefined,
    accountType: filters.accountType || undefined,
    currency: filters.currency || undefined,
    minBalance: parseAmount(filters.minBalance),
    maxBalance: parseAmount(filters.maxBalance),
    userId: filters.userId || undefined,
    page: targetPage,
    size: pageSize.value,
    sort: 'createdAt,desc',
  }
}

async function load(): Promise<void> {
  listError.value = null
  try {
    await adminStore.fetchAccounts(buildQuery(), true)
  } catch (cause) {
    listError.value = cause instanceof Error ? cause.message : 'We could not load the account register.'
  }
}

async function loadStats(): Promise<void> {
  try {
    // `fetchOverview` is where account stats come from; the page needs them for the strip only.
    await adminStore.fetchOverview()
  } catch {
    // The strip degrades to its own empty state; the register below still works.
  }
}

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
  filters.status = ''
  filters.accountType = ''
  filters.currency = ''
  filters.minBalance = ''
  filters.maxBalance = ''
  filters.userId = ''
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

function openAccount(account: Account): void {
  selectedAccount.value = account
  detailOpen.value = true
}

function ask(account: Account, action: Action): void {
  selectedAccount.value = account
  confirmAction.value = action
  reason.value = ''
  confirmOpen.value = true
}

const confirmTitle = computed(() =>
  confirmAction.value === 'block'
    ? 'Block this account'
    : confirmAction.value === 'activate'
      ? 'Activate this account'
      : 'Close this account',
)

const confirmMessage = computed(() => {
  const label = selectedAccount.value?.maskedAccountNumber ?? 'this account'
  if (confirmAction.value === 'block') {
    return `Block ${label}? Transfers in and out of the account stop immediately.`
  }
  if (confirmAction.value === 'activate') {
    return `Activate ${label}? The holder can send and receive money again.`
  }
  return `Close ${label}? Closing an account is permanent.`
})

const confirmDetail = computed(() => {
  const account = selectedAccount.value
  const balance = account ? formatMoney(account.balance, account.currency) : ''
  if (confirmAction.value === 'block') {
    return `The balance stays exactly where it is (${balance}) — blocking hides the account from the holder and refuses new transfers. It is reversible: activate the account to bring it back.`
  }
  if (confirmAction.value === 'activate') {
    return `Activating restores normal use of the account and its ${balance} balance. It does not resolve any fraud alert attached to a transfer — review those in the fraud console.`
  }
  return `Closing is only possible when the balance is exactly zero, and it is terminal: a closed account can never be reopened, so any future transfer to it will be rejected. Move the remaining money to another account first. Current balance: ${balance}.`
})

async function applyStatus(): Promise<void> {
  const account = selectedAccount.value
  if (!account || acting.value) return

  const status: AccountStatus =
    confirmAction.value === 'block' ? 'BLOCKED' : confirmAction.value === 'activate' ? 'ACTIVE' : 'CLOSED'

  try {
    await adminStore.setAccountStatus(account.id, status, reason.value.trim() || undefined)
    confirmOpen.value = false
    const updated: Account = { ...account, status }
    selectedAccount.value = updated
    toast.success(
      status === 'BLOCKED'
        ? 'Account blocked'
        : status === 'ACTIVE'
          ? 'Account activated'
          : 'Account closed',
      status === 'BLOCKED'
        ? `${account.maskedAccountNumber} cannot move money until it is activated again.`
        : status === 'ACTIVE'
          ? `${account.maskedAccountNumber} is open for transfers again.`
          : `${account.maskedAccountNumber} is closed permanently and cannot be reopened.`,
    )
    // A closed or blocked account may leave the current filter: refresh so the table matches reality.
    void load()
  } catch (cause) {
    toast.fromError(
      cause,
      status === 'BLOCKED'
        ? 'We could not block this account'
        : status === 'ACTIVE'
          ? 'We could not activate this account'
          : 'We could not close this account',
    )
  }
}

watch(() => [filters.status, filters.accountType, filters.currency, filters.minBalance, filters.maxBalance], () => reload())

onMounted(() => {
  void Promise.all([load(), loadStats()])
})
</script>

<template>
  <div class="space-y-5 pb-4">
    <PageHeader
      eyebrow="Finova Administration"
      title="Accounts"
      description="Every account on the platform, its owner, its currency and its current standing."
    >
      <template #actions>
        <p class="text-caption text-ink-subtle">
          <span aria-live="polite" class="font-semibold text-ink">
            {{ totalElements.toLocaleString('en-US') }}
          </span>
          account{{ totalElements === 1 ? '' : 's' }} matching
        </p>
      </template>
    </PageHeader>

    <!-- Balances at a glance -->
    <section class="fin-card p-4 sm:p-5" aria-labelledby="accounts-stats-heading">
      <h2 id="accounts-stats-heading" class="text-headline text-ink">Money and account counts</h2>
      <p class="mt-0.5 text-caption text-ink-muted">
        Held balances are shown per currency — they are never added together.
      </p>

      <div v-if="loading && !stats" class="mt-3" aria-busy="true">
        <Skeleton variant="block" :rows="2" />
        <span class="sr-only">Loading account totals</span>
      </div>

      <EmptyState
        v-else-if="!stats"
        compact
        title="Totals unavailable"
        description="The account service did not return totals for this session. The register below is still complete."
        :icon="Wallet"
      />

      <div v-else class="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <div class="min-w-0">
          <p class="fin-label">Balance held</p>
          <ul class="space-y-1.5">
            <li
              v-for="row in currencyTotals"
              :key="row.code"
              class="flex items-baseline justify-between gap-3 border-b border-border/60 pb-1.5 last:border-0"
            >
              <span class="text-caption font-semibold uppercase tracking-wider text-ink-subtle">
                {{ row.code }}
              </span>
              <span class="fin-amount text-[0.9375rem] text-ink">{{ formatMoney(row.total, row.code) }}</span>
            </li>
          </ul>
        </div>

        <div class="min-w-0">
          <p class="fin-label">Accounts by type</p>
          <ul class="space-y-1.5">
            <li
              v-for="row in typeTotals"
              :key="row.code"
              class="flex items-baseline justify-between gap-3 border-b border-border/60 pb-1.5 last:border-0"
            >
              <span class="text-[0.9375rem] text-ink">{{ row.code === 'SAVINGS' ? 'Savings' : 'Checking' }}</span>
              <span class="fin-amount text-[0.9375rem] text-ink">
                {{ row.count.toLocaleString('en-US') }}
              </span>
            </li>
          </ul>
          <p class="mt-2 text-caption text-ink-subtle">
            {{ stats.newAccountsLast30Days }} opened in the last 30 days
          </p>
        </div>

        <div class="min-w-0">
          <p class="fin-label">Standing</p>
          <dl class="space-y-1.5">
            <div class="flex items-baseline justify-between gap-3">
              <dt class="text-[0.9375rem] text-ink">Active</dt>
              <dd class="fin-amount text-[0.9375rem] text-success">
                {{ stats.activeAccounts.toLocaleString('en-US') }}
              </dd>
            </div>
            <div class="flex items-baseline justify-between gap-3">
              <dt class="text-[0.9375rem] text-ink">Blocked</dt>
              <dd class="fin-amount text-[0.9375rem] text-danger">
                {{ stats.blockedAccounts.toLocaleString('en-US') }}
              </dd>
            </div>
            <div class="flex items-baseline justify-between gap-3">
              <dt class="text-[0.9375rem] text-ink">Closed</dt>
              <dd class="fin-amount text-[0.9375rem] text-ink-muted">
                {{ stats.closedAccounts.toLocaleString('en-US') }}
              </dd>
            </div>
          </dl>
        </div>
      </div>
    </section>

    <div v-if="loading && accounts.length === 0 && !listError" class="fin-card p-5" aria-busy="true">
      <Skeleton variant="block" :rows="4" />
      <span class="sr-only">Loading the account register</span>
    </div>

    <ErrorState
      v-else-if="listError && accounts.length === 0"
      class="fin-card"
      title="We could not load the account register"
      :description="listError"
      retry-label="Reload accounts"
      @retry="load"
    />

    <template v-else>
      <FilterBar :active-count="activeFilterCount" @reset="onReset">
        <template #search>
          <SearchInput
            v-model="searchText"
            label="accounts"
            placeholder="Search account number, nickname or owner id"
            :debounce-ms="350"
            @search="onSearch"
          />
        </template>

        <template #primary>
          <BaseSelect
            v-model="filters.status"
            label="Status"
            :options="STATUS_OPTIONS"
            placeholder="All statuses"
            class="w-full sm:w-40"
          />
        </template>

        <template #advanced>
          <BaseSelect
            v-model="filters.accountType"
            label="Type"
            :options="TYPE_OPTIONS"
            placeholder="All types"
          />
          <BaseSelect
            v-model="filters.currency"
            label="Currency"
            :options="CURRENCY_OPTIONS"
            placeholder="All currencies"
          />
          <BaseInput
            v-model="filters.minBalance"
            label="Minimum balance"
            inputmode="decimal"
            placeholder="0.000"
          />
          <BaseInput
            v-model="filters.maxBalance"
            label="Maximum balance"
            inputmode="decimal"
            placeholder="No limit"
          />
        </template>
      </FilterBar>

      <p
        v-if="filters.userId"
        class="flex flex-wrap items-center gap-2 rounded-md bg-primary-soft px-3 py-2 text-caption text-ink-muted"
      >
        Showing accounts owned by
        <span class="font-mono font-semibold text-ink">{{ filters.userId }}</span>
        <button type="button" class="font-semibold text-primary underline" @click="filters.userId = ''">
          Clear owner filter
        </button>
      </p>

      <div
        v-if="listError"
        class="rounded-md bg-danger-light px-3 py-2.5 text-[0.875rem] text-danger-dark"
        role="alert"
      >
        {{ listError }}
        <button type="button" class="ml-2 font-semibold underline" @click="load">Try again</button>
      </div>

      <!-- Desktop -->
      <div v-if="accounts.length" class="fin-card hidden overflow-hidden lg:block">
        <div class="fin-scroll-thin overflow-x-auto">
          <table class="w-full min-w-[62rem] border-collapse">
            <caption class="sr-only">Finova account register</caption>
            <thead>
              <tr>
                <th scope="col" class="fin-table-header">Account</th>
                <th scope="col" class="fin-table-header">Owner</th>
                <th scope="col" class="fin-table-header">Type</th>
                <th scope="col" class="fin-table-header">Currency</th>
                <th scope="col" class="fin-table-header text-right">Balance</th>
                <th scope="col" class="fin-table-header">Status</th>
                <th scope="col" class="fin-table-header">Opened</th>
                <th scope="col" class="fin-table-header text-right">
                  <span class="sr-only">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="account in accounts"
                :key="account.id"
                class="cursor-pointer transition-colors hover:bg-surface-sunken"
                tabindex="0"
                @click="openAccount(account)"
                @keydown.enter="openAccount(account)"
                @keydown.space.prevent="openAccount(account)"
              >
                <td class="fin-table-cell">
                  <span class="block font-mono text-[0.8125rem] font-medium text-ink">
                    {{ maskAccountNumber(account.accountNumber) }}
                  </span>
                  <span class="mt-0.5 block truncate text-caption text-ink-subtle">
                    {{ account.nickname || 'Unnamed account' }}
                  </span>
                </td>
                <td class="fin-table-cell font-mono text-[0.8125rem] text-ink-muted">
                  {{ shortId(account.userId) }}
                </td>
                <td class="fin-table-cell text-[0.9375rem] text-ink-muted">
                  {{ account.accountType === 'SAVINGS' ? 'Savings' : 'Checking' }}
                </td>
                <td class="fin-table-cell text-[0.9375rem] text-ink-muted">{{ account.currency }}</td>
                <td class="fin-table-cell text-right">
                  <span
                    class="fin-amount text-[0.9375rem]"
                    :class="account.status === 'CLOSED' ? 'text-ink-subtle' : ''"
                  >
                    {{ formatMoney(account.balance, account.currency) }}
                  </span>
                </td>
                <td class="fin-table-cell">
                  <StatusBadge :status="account.status" />
                </td>
                <td class="fin-table-cell whitespace-nowrap text-[0.875rem] text-ink-muted">
                  {{ formatDateTime(account.createdAt) }}
                </td>
                <td class="fin-table-cell whitespace-nowrap text-right" @click.stop @keydown.stop>
                  <BaseButton
                    v-if="account.status === 'ACTIVE'"
                    variant="ghost"
                    size="sm"
                    :disabled="acting"
                    :aria-label="`Block account ${maskAccountNumber(account.accountNumber)}`"
                    @click="ask(account, 'block')"
                  >
                    <template #icon>
                      <Ban :size="14" aria-hidden="true" />
                    </template>
                    Block
                  </BaseButton>
                  <BaseButton
                    v-else-if="account.status === 'BLOCKED'"
                    variant="ghost"
                    size="sm"
                    :disabled="acting"
                    :aria-label="`Activate account ${maskAccountNumber(account.accountNumber)}`"
                    @click="ask(account, 'activate')"
                  >
                    <template #icon>
                      <CheckCircle2 :size="14" aria-hidden="true" />
                    </template>
                    Activate
                  </BaseButton>
                  <span
                    v-else
                    class="text-caption text-ink-subtle"
                    title="A closed account is terminal and cannot be reopened"
                  >
                    Terminal
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Mobile / tablet -->
      <ul v-if="accounts.length" class="fin-card divide-y divide-border lg:hidden">
        <li v-for="account in accounts" :key="account.id">
          <button
            type="button"
            class="flex w-full flex-col gap-2 p-4 text-left transition-colors hover:bg-surface-sunken"
            @click="openAccount(account)"
          >
            <span class="flex items-start justify-between gap-3">
              <span class="min-w-0">
                <span class="block font-mono text-[0.875rem] font-medium text-ink">
                  {{ maskAccountNumber(account.accountNumber) }}
                </span>
                <span class="mt-0.5 block truncate text-caption text-ink-subtle">
                  {{ account.nickname || 'Unnamed account' }}
                </span>
              </span>
              <span class="shrink-0 text-right">
                <span class="fin-amount block text-[0.9375rem] text-ink">
                  {{ formatMoney(account.balance, account.currency) }}
                </span>
                <span class="mt-0.5 block text-caption text-ink-subtle">{{ account.currency }}</span>
              </span>
            </span>

            <span class="flex flex-wrap items-center gap-2">
              <StatusBadge :status="account.status" size="sm" />
              <span class="text-caption text-ink-subtle">
                {{ account.accountType === 'SAVINGS' ? 'Savings' : 'Checking' }}
              </span>
              <time :datetime="account.createdAt" class="ml-auto text-caption text-ink-subtle">
                {{ formatDateTime(account.createdAt) }}
              </time>
            </span>

            <span class="font-mono text-caption text-ink-subtle">Owner {{ shortId(account.userId) }}</span>
          </button>

          <div
            v-if="account.status !== 'CLOSED'"
            class="flex flex-wrap items-center justify-end gap-2 border-t border-border px-4 pb-4 pt-3"
          >
            <BaseButton
              v-if="account.status === 'ACTIVE'"
              variant="secondary"
              size="sm"
              :disabled="acting"
              @click="ask(account, 'block')"
            >
              <template #icon>
                <Ban :size="14" aria-hidden="true" />
              </template>
              Block
            </BaseButton>
            <BaseButton
              v-else
              variant="secondary"
              size="sm"
              :disabled="acting"
              @click="ask(account, 'activate')"
            >
              <template #icon>
                <CheckCircle2 :size="14" aria-hidden="true" />
              </template>
              Activate
            </BaseButton>
            <BaseButton variant="ghost" size="sm" :disabled="acting" @click="ask(account, 'close')">
              <template #icon>
                <XCircle :size="14" aria-hidden="true" />
              </template>
              Close account
            </BaseButton>
          </div>
          <p
            v-else
            class="flex items-center gap-1.5 border-t border-border px-4 pb-4 pt-3 text-caption text-ink-subtle"
          >
            <Lock :size="13" aria-hidden="true" />
            Closed accounts cannot be reopened.
          </p>
        </li>
      </ul>

      <div v-if="!accounts.length" class="fin-card">
        <EmptyState
          :icon="Wallet"
          title="No accounts match these filters"
          description="No account on the platform matches the current search, status, type, currency or balance range."
          action-label="Reset filters"
          @action="onReset"
        />
      </div>

      <div v-if="accounts.length" class="fin-card px-4 py-3.5">
        <Pagination
          :page="page"
          :total-pages="totalPages"
          :total-elements="totalElements"
          :page-size="pageSize"
          label="accounts"
          @change="onPageChange"
          @size-change="onSizeChange"
        />
      </div>
    </template>

    <!-- Account detail -->
    <BaseModal
      :open="detailOpen"
      :title="selectedAccount ? maskAccountNumber(selectedAccount.accountNumber) : 'Account'"
      :description="selectedAccount ? (selectedAccount.nickname || 'Account detail') : undefined"
      size="lg"
      @close="detailOpen = false"
    >
      <div v-if="selectedAccount">
        <div class="flex flex-wrap items-center gap-2">
          <StatusBadge :status="selectedAccount.status" />
          <span class="fin-chip bg-surface-sunken text-ink-muted">
            {{ selectedAccount.accountType === 'SAVINGS' ? 'Savings' : 'Checking' }}
          </span>
          <span class="fin-chip bg-surface-sunken text-ink-muted">{{ selectedAccount.currency }}</span>
        </div>

        <div class="mt-4 rounded-md border border-border bg-surface-sunken p-4">
          <div class="flex flex-wrap items-start justify-between gap-3">
            <div class="min-w-0">
              <p class="text-caption font-semibold uppercase tracking-wider text-ink-subtle">
                Full account number
              </p>
              <p class="mt-1 break-all font-mono text-[0.9375rem] font-medium text-ink">
                {{ selectedAccount.accountNumber }}
              </p>
            </div>
            <CopyButton
              :value="selectedAccount.accountNumber"
              :label="`account number ${selectedAccount.accountNumber}`"
              size="sm"
            />
          </div>
        </div>

        <dl class="mt-4 divide-y divide-border border-t border-border">
          <DetailRow label="Balance" :value="formatMoney(selectedAccount.balance, selectedAccount.currency)" emphasis />
          <DetailRow
            label="Available balance"
            :value="formatMoney(selectedAccount.availableBalance, selectedAccount.currency)"
          />
          <DetailRow
            label="IBAN"
            :value="selectedAccount.iban ?? undefined"
            mono
            :hint="selectedAccount.iban ? undefined : 'No IBAN recorded for this account.'"
          >
            <span v-if="selectedAccount.iban" class="inline-flex flex-wrap items-center gap-2">
              <span class="break-all font-mono text-[0.8125rem]">{{ selectedAccount.iban }}</span>
              <CopyButton :value="selectedAccount.iban" label="IBAN" size="sm" />
            </span>
          </DetailRow>
          <DetailRow label="Owner" :value="selectedAccount.userId" mono />
          <DetailRow label="Bank" :value="selectedAccount.bankName" />
          <DetailRow label="Opened" :value="formatDateTime(selectedAccount.createdAt)" />
          <DetailRow label="Last updated" :value="formatDateTime(selectedAccount.updatedAt)" />
        </dl>
      </div>

      <template #footer>
        <BaseButton
          v-if="selectedAccount?.status === 'ACTIVE'"
          variant="danger"
          :disabled="acting"
          @click="selectedAccount && ask(selectedAccount, 'block')"
        >
          <template #icon>
            <Ban :size="15" aria-hidden="true" />
          </template>
          Block account
        </BaseButton>
        <BaseButton
          v-else-if="selectedAccount?.status === 'BLOCKED'"
          variant="primary"
          :disabled="acting"
          @click="selectedAccount && ask(selectedAccount, 'activate')"
        >
          <template #icon>
            <CheckCircle2 :size="15" aria-hidden="true" />
          </template>
          Activate account
        </BaseButton>
        <BaseButton
          v-if="selectedAccount && selectedAccount.status !== 'CLOSED'"
          variant="secondary"
          :disabled="acting"
          @click="ask(selectedAccount, 'close')"
        >
          <template #icon>
            <XCircle :size="15" aria-hidden="true" />
          </template>
          Close account
        </BaseButton>
        <BaseButton v-else variant="secondary" @click="detailOpen = false">Close</BaseButton>
      </template>
    </BaseModal>

    <ConfirmDialog
      :open="confirmOpen"
      :title="confirmTitle"
      :message="confirmMessage"
      :detail="confirmDetail"
      :confirm-label="
        confirmAction === 'block' ? 'Block account' : confirmAction === 'activate' ? 'Activate account' : 'Close account'
      "
      :tone="confirmAction === 'activate' ? 'primary' : 'danger'"
      :loading="acting"
      @confirm="applyStatus"
      @cancel="confirmOpen = false"
    >
      <BaseInput
        v-model="reason"
        label="Reason"
        :placeholder="confirmAction === 'close' ? 'Why this account is being closed' : 'Recorded in the audit trail'"
        :maxlength="180"
        hint="Stored with the audit entry for this status change."
      />
    </ConfirmDialog>
  </div>
</template>