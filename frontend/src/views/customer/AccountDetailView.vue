<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ArrowUpRight, Copy } from 'lucide-vue-next'
import ChartCard from '@/components/charts/ChartCard.vue'
import TransactionRow from '@/components/transaction/TransactionRow.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import DetailRow from '@/components/ui/DetailRow.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import Skeleton from '@/components/ui/Skeleton.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import { useAccountStore } from '@/stores/accountStore'
import { useToastStore } from '@/stores/toastStore'
import { useTransactionStore } from '@/stores/transactionStore'
import { formatAmount, formatDate, formatMoney } from '@/utils/format'

interface Movement {
  at: number
  delta: number
}

const CHART_WEEKS = 12
const LEDGER_PAGE_SIZE = 100

const route = useRoute()
const router = useRouter()
const accountStore = useAccountStore()
const transactionStore = useTransactionStore()
const toast = useToastStore()

const loading = ref(true)
const error = ref('')
const activityLoading = ref(true)
const activityError = ref('')

const accountId = computed(() => String(route.params.id ?? ''))
const account = computed(() => accountStore.byId[accountId.value] ?? null)
const accountLabel = computed(() => {
  const current = account.value
  if (!current) return 'Account'
  if (current.nickname) return current.nickname
  return current.accountType === 'SAVINGS' ? 'Savings account' : 'Everyday account'
})
const activityRows = computed(() =>
  [...transactionStore.transactions].sort((left, right) => right.createdAt.localeCompare(left.createdAt)),
)
const recentActivity = computed(() => activityRows.value.slice(0, 6))
const movements = computed<Movement[]>(() =>
  activityRows.value
    .filter(
      (transaction) =>
        transaction.status === 'COMPLETED' &&
        (transaction.senderAccountId === accountId.value ||
          transaction.receiverAccountId === accountId.value),
    )
    .map((transaction) => ({
      at: new Date(transaction.createdAt).getTime(),
      delta:
        transaction.senderAccountId === accountId.value ? -transaction.amount : transaction.amount,
    }))
    .filter((movement) => Number.isFinite(movement.at)),
)

function round3(value: number): number {
  return Math.round(value * 1000) / 1000
}

function weekEndAt(weeksAgo: number): number {
  const end = new Date()
  end.setHours(23, 59, 59, 999)
  end.setDate(end.getDate() - weeksAgo * 7)
  return end.getTime()
}

const balanceChart = computed(() => {
  const history = [...movements.value].sort((left, right) => left.at - right.at)
  const current = account.value?.balance ?? 0
  const oldestKnown = history.length ? history[0].at : Number.POSITIVE_INFINITY

  const labels: string[] = []
  const data: Array<number | null> = []
  for (let weeksAgo = CHART_WEEKS - 1; weeksAgo >= 0; weeksAgo -= 1) {
    const at = weekEndAt(weeksAgo)
    labels.push(formatDate(new Date(at).toISOString()))
    if (at < oldestKnown) {
      data.push(null)
      continue
    }
    const movedAfter = history.reduce((sum, movement) => (movement.at > at ? sum + movement.delta : sum), 0)
    data.push(round3(current - movedAfter))
  }

  return { labels, data }
})

const balanceCaption = computed(() => {
  const count = movements.value.length
  if (!count) return ''
  const oldest = Math.min(...movements.value.map((movement) => movement.at))
  const weeks = Math.max(1, Math.ceil((Date.now() - oldest) / (7 * 24 * 60 * 60 * 1000)))
  const scope = weeks < CHART_WEEKS ? ` covering the last ${weeks} week${weeks === 1 ? '' : 's'}` : ''
  return `Reconstructed from ${count} recorded movement${count === 1 ? '' : 's'}${scope}. Earlier weeks are left blank because Finova holds no record of them.`
})

function messageOf(cause: unknown, fallback: string): string {
  return cause instanceof Error && cause.message ? cause.message : fallback
}

async function loadActivity(force: boolean): Promise<void> {
  activityLoading.value = true
  activityError.value = ''
  try {
    await transactionStore.fetchList(
      { accountId: accountId.value, page: 0, size: LEDGER_PAGE_SIZE, sort: 'createdAt,asc' },
      force,
    )
    const total = transactionStore.page?.totalElements ?? 0
    if (total > activityRows.value.length) {
      await transactionStore.fetchList(
        { accountId: accountId.value, page: 0, size: LEDGER_PAGE_SIZE, sort: 'createdAt,desc' },
        true,
      )
    }
  } catch (cause) {
    activityError.value = messageOf(cause, 'We could not load the activity for this account.')
  } finally {
    activityLoading.value = false
  }
}

async function load(force = false): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    await accountStore.fetchOne(accountId.value, force)
  } catch (cause) {
    error.value = messageOf(cause, 'We could not load this account.')
  } finally {
    loading.value = false
  }
  if (!error.value) await loadActivity(force)
}

async function copyAccountNumber(): Promise<void> {
  const current = account.value
  if (!current) return
  try {
    await navigator.clipboard.writeText(current.accountNumber)
    toast.success('Account number copied', 'Paste it wherever you need to receive money.')
  } catch {
    toast.error('Could not copy the account number', 'Select the number and copy it manually.')
  }
}

function goToAccounts(): void {
  void router.push({ name: 'accounts' })
}

function goToTransfer(): void {
  void router.push({ name: 'transfer', query: { account: accountId.value } })
}

function goToTransactions(): void {
  void router.push({ name: 'transactions', query: { account: accountId.value } })
}

function goToTransaction(transactionId: string): void {
  void router.push({ name: 'transaction-detail', params: { id: transactionId } })
}

watch(accountId, () => void load(), { immediate: true })
</script>

<template>
  <div class="space-y-5">
    <BaseButton variant="ghost" size="sm" @click="goToAccounts">
      <template #icon>
        <ArrowLeft :size="15" aria-hidden="true" />
      </template>
      All accounts
    </BaseButton>

    <div v-if="loading" class="space-y-5">
      <Skeleton width="12rem" />
      <Skeleton width="20rem" />
      <div class="fin-card p-4 sm:p-5">
        <Skeleton variant="block" :rows="4" />
      </div>
    </div>

    <div v-else-if="error" class="fin-card">
      <ErrorState title="We could not open this account" :description="error">
        <BaseButton variant="secondary" @click="goToAccounts">Back to accounts</BaseButton>
      </ErrorState>
    </div>

    <template v-else-if="account">
      <PageHeader
        :eyebrow="account.maskedAccountNumber"
        :title="accountLabel"
        :description="`${account.accountType === 'SAVINGS' ? 'Savings account' : 'Checking account'} in ${account.currency}, opened ${formatDate(account.createdAt)}.`"
      >
        <template #actions>
          <StatusBadge :status="account.status" />
          <BaseButton @click="goToTransfer">
            <template #icon>
              <ArrowUpRight :size="16" aria-hidden="true" />
            </template>
            Send money
          </BaseButton>
        </template>
      </PageHeader>

      <div class="grid gap-4 lg:grid-cols-5 lg:gap-5">
        <section class="fin-card p-4 sm:p-5 lg:col-span-3" aria-labelledby="account-details-heading">
          <h2 id="account-details-heading" class="text-headline text-ink">Account details</h2>

          <div class="mt-4 rounded-md border border-border bg-surface-sunken px-4 py-3">
            <p class="text-caption font-semibold uppercase tracking-wider text-ink-subtle">
              Current balance
            </p>
            <p class="fin-amount mt-1 text-money text-ink">
              {{ formatAmount(account.balance) }}
              <span class="ml-1 text-base font-semibold text-ink-muted">{{ account.currency }}</span>
            </p>
          </div>

          <dl class="mt-1">
            <DetailRow
              label="Account number"
              mono
            >
              <span class="break-all">{{ account.accountNumber }}</span>
              <button
                type="button"
                class="ml-1.5 inline-flex shrink-0 rounded p-1 align-middle text-ink-subtle transition-colors hover:bg-primary-soft hover:text-primary fin-focus-ring"
                aria-label="Copy account number"
                @click="copyAccountNumber"
              >
                <Copy :size="15" aria-hidden="true" />
              </button>
            </DetailRow>

            <DetailRow
              label="Available balance"
              emphasis
              :value="formatMoney(account.availableBalance, account.currency)"
            />

            <DetailRow
              label="Account type"
              :value="account.accountType === 'SAVINGS' ? 'Savings account' : 'Checking account'"
            />

            <DetailRow label="Currency" :value="account.currency" />

            <DetailRow label="Status">
              <StatusBadge :status="account.status" size="sm" />
            </DetailRow>

            <DetailRow label="Created" :value="formatDate(account.createdAt)" />

            <DetailRow label="Last updated" :value="formatDate(account.updatedAt)" />
          </dl>
        </section>

        <div class="lg:col-span-2">
          <ChartCard
            v-if="movements.length"
            type="line"
            title="Balance — last 12 weeks"
            :subtitle="balanceCaption"
            :labels="balanceChart.labels"
            :datasets="[{ label: 'Balance', data: balanceChart.data }]"
            :currency="account.currency"
            :height="240"
            empty-message="Not enough history to chart this balance yet."
          />

          <div v-else-if="activityError" class="fin-card">
            <ErrorState
              compact
              title="We could not load the balance history"
              :description="activityError"
              @retry="loadActivity(true)"
            />
          </div>

          <div v-else-if="activityLoading" class="fin-card p-4 sm:p-5">
            <Skeleton variant="block" :rows="4" />
          </div>

          <div v-else class="fin-card">
            <EmptyState
              compact
              title="No movements yet"
              description="Once money moves through this account, its balance history appears here."
              action-label="Send money"
              @action="goToTransfer"
            />
          </div>
        </div>
      </div>

      <section class="fin-card p-4 sm:p-5" aria-labelledby="account-activity-heading">
        <header class="mb-3 flex items-center justify-between gap-3">
          <h2 id="account-activity-heading" class="text-headline text-ink">Recent activity</h2>
          <RouterLink
            :to="{ name: 'transactions', query: { account: accountId } }"
            class="text-[0.8125rem] font-semibold text-primary hover:underline"
          >
            View all transactions
          </RouterLink>
        </header>

        <div v-if="activityLoading" class="py-2">
          <Skeleton variant="block" :rows="4" />
        </div>

        <ErrorState
          v-else-if="activityError"
          compact
          title="We could not load this activity"
          :description="activityError"
          @retry="loadActivity(true)"
        />

        <EmptyState
          v-else-if="!recentActivity.length"
          compact
          title="Nothing recorded yet"
          description="Transfers, deposits and withdrawals on this account will be listed here."
          action-label="Send money"
          @action="goToTransfer"
        />

        <ul v-else class="-mx-2 divide-y divide-border/70">
          <li v-for="transaction in recentActivity" :key="transaction.id">
            <TransactionRow
              :transaction="transaction"
              :perspective="transaction.senderAccountId === account.id ? 'sender' : 'receiver'"
              @click="goToTransaction(transaction.id)"
            />
          </li>
        </ul>
      </section>
    </template>
  </div>
</template>