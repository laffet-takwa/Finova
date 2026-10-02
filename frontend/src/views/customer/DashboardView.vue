<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { ArrowDownLeft, ArrowLeftRight, ArrowUpRight, Wallet } from 'lucide-vue-next'
import AccountCard from '@/components/account/AccountCard.vue'
import BalanceCard from '@/components/account/BalanceCard.vue'
import ChartCard from '@/components/charts/ChartCard.vue'
import TransactionRow from '@/components/transaction/TransactionRow.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import Skeleton from '@/components/ui/Skeleton.vue'
import StatCard from '@/components/ui/StatCard.vue'
import { useAccountStore } from '@/stores/accountStore'
import { useAuthStore } from '@/stores/authStore'
import { useTransactionStore } from '@/stores/transactionStore'
import { formatSignedMoney, greetingFor } from '@/utils/format'
import type { Transaction } from '@/types'

const CASH_FLOW_COLORS = { income: '#16A34A', expenses: '#DC2626' } as const

const router = useRouter()
const auth = useAuthStore()
const accountStore = useAccountStore()
const transactionStore = useTransactionStore()

const accountsLoading = ref(true)
const accountsError = ref('')
const summaryLoading = ref(true)
const summaryError = ref('')
const listLoading = ref(true)
const listError = ref('')
const balancesHidden = ref(false)

const greeting = computed(() => `${greetingFor()}, ${auth.user?.firstName ?? 'there'}`)
const summary = computed(() => transactionStore.summary)
const summaryCurrency = computed(() => summary.value?.currency ?? accountStore.primaryCurrency)
const myAccountIds = computed(() => new Set(accountStore.accounts.map((account) => account.id)))
const recentTransactions = computed(() =>
  [...transactionStore.transactions]
    .sort((left, right) => right.createdAt.localeCompare(left.createdAt))
    .slice(0, 8),
)
const hasTransactions = computed(
  () => (summary.value?.transactionCount ?? 0) > 0 || recentTransactions.value.length > 0,
)
const cashFlowLabels = computed(() => dailySeries.value.map((point) => point.label))
const dailySeries = computed(() => (summary.value?.dailySeries ?? []).slice(-30))
const cashFlowDatasets = computed(() => [
  {
    label: 'Income',
    data: dailySeries.value.map((point) => point.income),
    color: CASH_FLOW_COLORS.income,
  },
  {
    label: 'Expenses',
    data: dailySeries.value.map((point) => point.expenses),
    color: CASH_FLOW_COLORS.expenses,
  },
])

function messageOf(cause: unknown, fallback: string): string {
  return cause instanceof Error && cause.message ? cause.message : fallback
}

function perspectiveFor(transaction: Transaction): 'sender' | 'receiver' | 'neutral' {
  if (myAccountIds.value.has(transaction.senderAccountId)) return 'sender'
  if (myAccountIds.value.has(transaction.receiverAccountId)) return 'receiver'
  return 'neutral'
}

async function load(force = false): Promise<void> {
  accountsLoading.value = true
  summaryLoading.value = true
  listLoading.value = true

  const [accountsResult, summaryResult, listResult] = await Promise.allSettled([
    accountStore.fetchAll(force),
    transactionStore.fetchSummary(force),
    transactionStore.fetchList({ page: 0, size: 8, sort: 'createdAt,desc' }, force),
  ])

  accountsError.value =
    accountsResult.status === 'rejected'
      ? messageOf(accountsResult.reason, 'We could not load your accounts.')
      : ''
  listError.value =
    listResult.status === 'rejected'
      ? messageOf(listResult.reason, 'We could not load your recent transactions.')
      : ''
  summaryError.value =
    summaryResult.status === 'rejected' || summaryResult.value === null
      ? messageOf(
          summaryResult.status === 'rejected' ? summaryResult.reason : transactionStore.error,
          'We could not load your activity summary.',
        )
      : ''

  accountsLoading.value = false
  summaryLoading.value = false
  listLoading.value = false
}

async function retryAccounts(): Promise<void> {
  accountsLoading.value = true
  try {
    await accountStore.fetchAll(true)
    accountsError.value = ''
  } catch (cause) {
    accountsError.value = messageOf(cause, 'We could not load your accounts.')
  } finally {
    accountsLoading.value = false
  }
}

async function retrySummary(): Promise<void> {
  summaryLoading.value = true
  try {
    const result = await transactionStore.fetchSummary(true)
    summaryError.value = result ? '' : messageOf(transactionStore.error, 'We could not load your activity summary.')
  } finally {
    summaryLoading.value = false
  }
}

async function retryList(): Promise<void> {
  listLoading.value = true
  try {
    await transactionStore.fetchList({ page: 0, size: 8, sort: 'createdAt,desc' }, true)
    listError.value = ''
  } catch (cause) {
    listError.value = messageOf(cause, 'We could not load your recent transactions.')
  } finally {
    listLoading.value = false
  }
}

function goToTransfer(): void {
  void router.push({ name: 'transfer' })
}

function goToTransferFrom(accountId: string): void {
  void router.push({ name: 'transfer', query: { account: accountId } })
}

function goToAccounts(): void {
  void router.push({ name: 'accounts' })
}

function goToAccount(accountId: string): void {
  void router.push({ name: 'account-detail', params: { id: accountId } })
}

function goToTransaction(transactionId: string): void {
  void router.push({ name: 'transaction-detail', params: { id: transactionId } })
}

onMounted(() => {
  if (auth.isAdmin) {
    void router.replace({ name: 'admin-dashboard' })
    return
  }
  void load()
})
</script>

<template>
  <div class="space-y-5">
    <PageHeader :title="greeting" description="Here's your financial overview." />

    <div v-if="accountsError" class="fin-card">
      <ErrorState
        title="We could not load your balance"
        :description="accountsError"
        @retry="retryAccounts"
      />
    </div>
    <BalanceCard
      v-else
      :total="accountStore.totalBalance"
      :currency="accountStore.primaryCurrency"
      :monthly-change-percent="transactionStore.summary?.monthChangePercent ?? null"
      :account-count="accountStore.accounts.length"
      :loading="accountsLoading"
      :hidden="balancesHidden"
      @send="goToTransfer"
      @view-accounts="goToAccounts"
      @toggle-visibility="balancesHidden = !balancesHidden"
    />

    <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <StatCard
        label="Income"
        :value="formatSignedMoney(summary?.income ?? 0, summaryCurrency)"
        tone="success"
        hint="Money received this month"
        :loading="summaryLoading && !summary"
      >
        <template #icon>
          <ArrowDownLeft :size="17" />
        </template>
      </StatCard>

      <StatCard
        label="Expenses"
        :value="formatSignedMoney(-(summary?.expenses ?? 0), summaryCurrency)"
        tone="danger"
        hint="Money sent this month"
        :loading="summaryLoading && !summary"
      >
        <template #icon>
          <ArrowUpRight :size="17" />
        </template>
      </StatCard>

      <StatCard
        label="Transactions"
        :value="String(summary?.transactionCount ?? 0)"
        tone="primary"
        hint="Recorded on your accounts"
        :loading="summaryLoading && !summary"
      >
        <template #icon>
          <ArrowLeftRight :size="17" />
        </template>
      </StatCard>
    </div>

    <p v-if="summary" class="sr-only" aria-live="polite">
      This month: income {{ formatSignedMoney(summary.income, summaryCurrency) }}, expenses
      {{ formatSignedMoney(-summary.expenses, summaryCurrency) }}, {{ summary.transactionCount }}
      transactions.
    </p>

    <div class="grid gap-4 lg:grid-cols-5 lg:gap-5">
      <ChartCard
        v-if="!summaryError && hasTransactions"
        class="lg:col-span-3"
        type="bar"
        title="Cash flow — last 30 days"
        subtitle="Money in and money out, per day"
        :labels="cashFlowLabels"
        :datasets="cashFlowDatasets"
        :currency="summaryCurrency"
        :height="280"
        :loading="summaryLoading"
      />

      <div v-else-if="summaryError" class="fin-card lg:col-span-3">
        <ErrorState
          title="We could not load your cash flow"
          :description="summaryError"
          @retry="retrySummary"
        />
      </div>

      <ChartCard
        v-else-if="summaryLoading"
        class="lg:col-span-3"
        type="bar"
        title="Cash flow — last 30 days"
        :labels="[]"
        :datasets="[]"
        :height="280"
        loading
      />

      <div v-else class="fin-card lg:col-span-3">
        <EmptyState
          title="No transactions yet"
          description="Once money moves in or out, your last 30 days of cash flow will be charted here."
          action-label="Send money"
          @action="goToTransfer"
        />
      </div>

      <section
        class="fin-card min-w-0 overflow-hidden p-4 sm:p-5 lg:col-span-2"
        aria-labelledby="dashboard-accounts-heading"
      >
        <header class="mb-4 flex items-center justify-between gap-3">
          <h2 id="dashboard-accounts-heading" class="text-headline text-ink">Accounts</h2>
          <RouterLink
            :to="{ name: 'accounts' }"
            class="text-[0.8125rem] font-semibold text-primary hover:underline"
          >
            View all
          </RouterLink>
        </header>

        <div v-if="accountsLoading" class="grid gap-4 sm:grid-cols-2 lg:grid-cols-1">
          <div v-for="placeholder in 2" :key="placeholder" class="fin-card p-5">
            <Skeleton variant="block" :rows="3" />
          </div>
        </div>

        <ErrorState
          v-else-if="accountsError"
          compact
          title="We could not load your accounts"
          :description="accountsError"
          @retry="retryAccounts"
        />

        <EmptyState
          v-else-if="!accountStore.accounts.length"
          compact
          title="No accounts yet"
          description="Open an account to start sending and receiving money."
          action-label="Open an account"
          @action="goToAccounts"
        />

        <div v-else class="-mx-4 px-4 sm:mx-0 sm:px-0">
          <div
            class="fin-scroll-thin flex snap-x snap-mandatory gap-4 overflow-x-auto pb-2 sm:grid sm:grid-cols-2 sm:overflow-x-visible sm:pb-0 lg:grid-cols-1"
          >
            <div
              v-for="account in accountStore.accounts"
              :key="account.id"
              class="w-[16rem] shrink-0 snap-start sm:w-auto"
            >
              <AccountCard
                :account="account"
                @view="goToAccount(account.id)"
                @transfer="goToTransferFrom(account.id)"
              />
            </div>
          </div>
        </div>
      </section>
    </div>

    <section class="fin-card p-4 sm:p-5" aria-labelledby="recent-transactions-heading">
      <header class="mb-3 flex items-center justify-between gap-3">
        <h2 id="recent-transactions-heading" class="text-headline text-ink">Recent transactions</h2>
        <RouterLink
          :to="{ name: 'transactions' }"
          class="text-[0.8125rem] font-semibold text-primary hover:underline"
        >
          View all
        </RouterLink>
      </header>

      <div v-if="listLoading" class="py-2">
        <Skeleton variant="block" :rows="4" />
      </div>

      <ErrorState
        v-else-if="listError"
        compact
        title="We could not load your transactions"
        :description="listError"
        @retry="retryList"
      />

      <EmptyState
        v-else-if="!recentTransactions.length"
        compact
        title="Nothing to show yet"
        description="Your eight most recent movements will appear here as soon as money moves."
        :icon="Wallet"
        action-label="Send money"
        @action="goToTransfer"
      />

      <ul v-else class="-mx-2 divide-y divide-border/70">
        <li v-for="transaction in recentTransactions" :key="transaction.id">
          <TransactionRow
            :transaction="transaction"
            :perspective="perspectiveFor(transaction)"
            @click="goToTransaction(transaction.id)"
          />
        </li>
      </ul>
    </section>
  </div>
</template>