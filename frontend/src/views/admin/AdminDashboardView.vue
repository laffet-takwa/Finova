<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowLeftRight,
  ArrowRight,
  FileSearch,
  RefreshCw,
  ShieldAlert,
  TrendingUp,
  UserPlus,
  Users,
  Wallet,
} from 'lucide-vue-next'
import AuditEntryRow from '@/components/ui/AuditEntryRow.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import ChartCard from '@/components/charts/ChartCard.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import RiskBadge from '@/components/ui/RiskBadge.vue'
import Skeleton from '@/components/ui/Skeleton.vue'
import StatCard from '@/components/ui/StatCard.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import { useAdminStore } from '@/stores/adminStore'
import { useFraudStore } from '@/stores/fraudStore'
import { formatCompact, formatMoney, formatPercent, formatRelative } from '@/utils/format'
import type { FraudAlert } from '@/types'

/** Palette values from tailwind.config.js — a chart needs concrete colours, markup does not. */
const CHART = {
  volume: '#173B5F',
  throughput: '#25527D',
  failure: '#DC2626',
  alerts: '#B45309',
  growth: '#3B82F6',
} as const

/** Above this value a headline amount is abbreviated so the card does not wrap. */
const COMPACT_FROM = 1_000_000

/** Above this many open alerts the risk queue becomes the console's priority. */
const ALERT_PRESSURE_THRESHOLD = 8

const router = useRouter()
const adminStore = useAdminStore()
const fraudStore = useFraudStore()

const overviewError = ref<string | null>(null)
const fraudError = ref<string | null>(null)
const lastUpdated = ref<string | null>(null)
const refreshing = ref(false)

const overviewLoading = computed(() => adminStore.loading && !adminStore.isReady)
const fraudLoading = computed(() => fraudStore.loading)

const userStats = computed(() => adminStore.userStats)
const accountStats = computed(() => adminStore.accountStats)
const transactionStats = computed(() => adminStore.transactionStats)
const fraudStats = computed(() => fraudStore.stats)

/** True once the batch has settled with no transaction stats to show — a real gap, not a load. */
const transactionStatsMissing = computed(() => !overviewLoading.value && transactionStats.value === null)
const alertStatsMissing = computed(() => !fraudLoading.value && fraudStats.value === null)

/** Unresolved alerts straight from the risk queue, highest score first. */
const riskyAlerts = computed<FraudAlert[]>(() => fraudStore.alerts.filter((alert) => alert.status !== 'SAFE' && alert.status !== 'CONFIRMED').slice(0, 4))

const dailyVolume = computed(() => transactionStats.value?.dailyVolume ?? [])
const dailyVolumeLabels = computed(() => dailyVolume.value.map((point) => point.label))
/**
 * `SeriesPoint.total` carries money when the backend reports value; when it only
 * reports `count` the series is a number of transfers, and labelling it as TND
 * would be a lie. The chart follows the data rather than the wish.
 */
const dailyVolumeIsMoney = computed(
  () => dailyVolume.value.length > 0 && dailyVolume.value.every((point) => typeof point.total === 'number'),
)
const dailyVolumeSubtitle = computed(() =>
  dailyVolumeIsMoney.value
    ? 'Settled value per day, last 30 days'
    : 'Settled transfers per day, last 30 days (this stats endpoint reports counts, not value)',
)
const dailyVolumeDatasets = computed(() => [
  {
    label: dailyVolumeIsMoney.value ? 'Settled value' : 'Settled transfers',
    data: dailyVolume.value.map((point) =>
      dailyVolumeIsMoney.value ? (point.total ?? 0) : point.count,
    ),
    color: CHART.volume,
  },
])

const hourlyVolume = computed(() => transactionStats.value?.hourlyVolume ?? [])
const hourlyLabels = computed(() => hourlyVolume.value.map((point) => point.label))
const hourlySettled = computed(() => hourlyVolume.value.map((point) => point.count))
/**
 * The transaction stats endpoint reports one decided-transfer count per hour plus
 * today's completed and failed totals — it has no per-hour failure series. The
 * failed bars are therefore today's real failure share spread over the same hours,
 * and the subtitle states that rather than implying they were measured per hour.
 */
const failureShare = computed(() => {
  const stats = transactionStats.value
  if (!stats) return 0
  const decided = stats.completedToday + stats.failedToday
  return decided > 0 ? stats.failedToday / decided : 0
})
const hourlyFailed = computed(() =>
  hourlyVolume.value.map((point) => Math.max(0, Math.round(point.count * failureShare.value))),
)
const hourlyDatasets = computed(() => [
  { label: 'Settled', data: hourlySettled.value, color: CHART.throughput },
  { label: 'Failed', data: hourlyFailed.value, color: CHART.failure },
])
const hourlySubtitle = computed(() =>
  failureShare.value > 0
    ? `Settled is the per-hour series reported by the transaction service. Failed is today's ${formatPercent(failureShare.value * 100)} failure share spread over the same hours, because failures are counted per day rather than per hour.`
    : 'Settled transfers per hour. No transfers were decided today, so there is no failure share to show.',
)

const dailyAlerts = computed(() => fraudStats.value?.dailyAlerts ?? [])
const dailyAlertLabels = computed(() => dailyAlerts.value.map((point) => point.label))
const dailyAlertDatasets = computed(() => [
  { label: 'Alerts raised', data: dailyAlerts.value.map((point) => point.count), color: CHART.alerts },
])

const userGrowth = computed(() => userStats.value?.growthSeries ?? [])
const userGrowthLabels = computed(() => userGrowth.value.map((point) => point.label))
const userGrowthDatasets = computed(() => [
  { label: 'Registered users', data: userGrowth.value.map((point) => point.count), color: CHART.growth },
])

const totalUsers = computed(() =>
  userStats.value === null ? '—' : userStats.value.totalUsers.toLocaleString('en-US'),
)
const activeAccounts = computed(() =>
  accountStats.value === null ? '—' : accountStats.value.activeAccounts.toLocaleString('en-US'),
)
const completedToday = computed(() =>
  transactionStats.value === null ? '—' : transactionStats.value.completedToday.toLocaleString('en-US'),
)
const volumeToday = computed(() => formatMoney(transactionStats.value?.volumeToday ?? 0, 'TND'))
/** Headline money is abbreviated above a million so the card never wraps mid-figure. */
const volumeTodayCard = computed(() => {
  const stats = transactionStats.value
  if (stats === null) return '—'
  return stats.volumeToday >= COMPACT_FROM
    ? `${formatCompact(stats.volumeToday)} TND`
    : formatMoney(stats.volumeToday, 'TND')
})
const alertsTone = computed<'neutral' | 'danger'>(() =>
  adminStore.openAlertCount > ALERT_PRESSURE_THRESHOLD ? 'danger' : 'neutral',
)
/** The count is the `status=OPEN` total, so it is labelled open — not unresolved. */
const alertsHint = computed(() =>
  adminStore.openAlertCount > ALERT_PRESSURE_THRESHOLD
    ? `Above the ${ALERT_PRESSURE_THRESHOLD}-alert review threshold`
    : 'Open, no decision taken yet',
)
const lastUpdatedLabel = computed(() => (lastUpdated.value ? formatRelative(lastUpdated.value) : 'not yet'))

/** Captions below the quick actions show a dash rather than a false zero while loading. */
const openAlertSummary = computed(() => {
  if (!adminStore.loaded) return 'Counting open alerts…'
  const count = adminStore.openAlertCount
  return `${count.toLocaleString('en-US')} open alert${count === 1 ? '' : 's'} waiting on a decision`
})

const userSummary = computed(() => {
  if (!userStats.value) return 'Counting blocked and new accounts…'
  return `${userStats.value.blockedUsers.toLocaleString('en-US')} blocked · ${userStats.value.newUsersThisMonth.toLocaleString('en-US')} joined this month`
})

async function loadOverview(force = false): Promise<void> {
  refreshing.value = true
  try {
    await adminStore.fetchOverview(force)
    overviewError.value = adminStore.error
    lastUpdated.value = new Date().toISOString()
  } catch (cause) {
    overviewError.value =
      cause instanceof Error ? cause.message : 'We could not load the administration overview.'
  } finally {
    refreshing.value = false
  }
}

async function loadFraud(force = false): Promise<void> {
  fraudError.value = null
  const [alerts, stats] = await Promise.allSettled([
    fraudStore.fetchAlerts({ page: 0, size: 6 }, force),
    fraudStore.fetchStats(force),
  ])
  if (alerts.status === 'rejected' && stats.status === 'rejected') {
    fraudError.value =
      alerts.status === 'rejected'
        ? alerts.reason instanceof Error
          ? alerts.reason.message
          : 'We could not load the risk queue.'
        : 'We could not load fraud statistics.'
  }
}

async function refreshAll(): Promise<void> {
  await Promise.all([loadOverview(true), loadFraud(true)])
}

function goToFraud(): void {
  void router.push({ name: 'admin-fraud' })
}

function goToTransactions(): void {
  void router.push({ name: 'admin-transactions' })
}

function goToAlert(id: string): void {
  void router.push({ name: 'admin-fraud-detail', params: { id } })
}

onMounted(() => {
  void Promise.all([loadOverview(), loadFraud()])
  // The store owns the two-minute timer; this view only opens and closes it.
  adminStore.startPolling()
})

onBeforeUnmount(() => {
  adminStore.stopPolling()
})
</script>

<template>
  <div class="space-y-5">
    <PageHeader
      eyebrow="Finova Administration"
      title="System overview and risk monitoring"
      description="Platform-wide figures for customers, accounts, movement and the fraud queue. Everything on this page refreshes on its own every two minutes."
    >
      <template #actions>
        <p class="flex items-center gap-2 text-caption text-ink-subtle">
          <span class="relative flex h-2 w-2" aria-hidden="true">
            <span class="absolute inline-flex h-full w-full animate-ping rounded-full bg-success opacity-60" />
            <span class="relative inline-flex h-2 w-2 rounded-full bg-success" />
          </span>
          <span>Live · updated {{ lastUpdatedLabel }}</span>
        </p>
        <BaseButton
          variant="secondary"
          size="sm"
          :loading="refreshing"
          :disabled="refreshing"
          @click="refreshAll"
        >
          <template #icon>
            <RefreshCw :size="15" aria-hidden="true" />
          </template>
          Refresh
        </BaseButton>
      </template>
    </PageHeader>

    <!-- The overview is one allSettled batch, so a partial failure is expected, not fatal. -->
    <ErrorState
      v-if="overviewError && !adminStore.isReady"
      class="fin-card"
      title="We could not load the administration overview"
      :description="overviewError"
      retry-label="Reload the overview"
      @retry="loadOverview(true)"
    />

    <div
      v-else-if="overviewError"
      class="rounded-md bg-danger-light px-3 py-2.5 text-[0.875rem] text-danger-dark"
      role="alert"
    >
      {{ overviewError }} Some panels below may be out of date.
      <button type="button" class="ml-2 font-semibold underline" @click="loadOverview(true)">
        Try again
      </button>
    </div>

<!-- 2 on mobile, 3 on tablet, 5 once the sidebar leaves room. -->
    <div class="grid grid-cols-2 gap-4 sm:grid-cols-3 sm:gap-5 xl:grid-cols-5">
      <StatCard
        label="Total Users"
        :value="totalUsers"
        :loading="overviewLoading"
        :hint="userStats ? `${userStats.newUsersThisMonth} joined this month` : 'Customers and administrators'"
      >
        <template #icon>
          <Users :size="17" />
        </template>
      </StatCard>

      <StatCard
        label="Active Accounts"
        :value="activeAccounts"
        :loading="overviewLoading"
        :hint="accountStats ? `${accountStats.blockedAccounts} blocked · ${accountStats.closedAccounts} closed` : 'Open for transfers'"
      >
        <template #icon>
          <Wallet :size="17" />
        </template>
      </StatCard>

      <StatCard
        label="Transactions Today"
        :value="completedToday"
        :loading="overviewLoading"
        :hint="transactionStats ? `${transactionStats.failedToday} failed · ${transactionStats.flaggedCount} flagged` : 'Settled today'"
      >
        <template #icon>
          <ArrowLeftRight :size="17" />
        </template>
      </StatCard>

<StatCard
        label="Transaction Volume"
        :value="volumeTodayCard"
        tone="primary"
        :loading="overviewLoading"
        :hint="transactionStats ? `${formatPercent(transactionStats.successRate)} of decided transfers settled` : 'Settled today'"
      >
        <template #icon>
          <TrendingUp :size="17" />
        </template>
      </StatCard>

      <StatCard
        label="Fraud Alerts"
        :value="adminStore.openAlertCount.toLocaleString('en-US')"
        :tone="alertsTone"
        :loading="overviewLoading"
        :hint="alertsHint"
      >
        <template #icon>
          <ShieldAlert :size="17" />
        </template>
      </StatCard>
    </div>

    <div class="grid gap-4 lg:grid-cols-2 lg:gap-5">
<ChartCard
        v-if="!transactionStatsMissing"
        class="min-w-0"
        type="bar"
        title="Transaction volume"
        :subtitle="dailyVolumeSubtitle"
        :labels="dailyVolumeLabels"
        :datasets="dailyVolumeDatasets"
        :currency="dailyVolumeIsMoney ? 'TND' : ''"
        :money-format="dailyVolumeIsMoney"
        :height="250"
        :loading="overviewLoading"
        empty-message="No settled transfers in this period."
      >
        <template #actions>
          <p class="text-caption text-ink-subtle">{{ volumeToday }} settled today</p>
        </template>
      </ChartCard>

      <div v-else class="fin-card p-4 sm:p-5">
        <ErrorState
          compact
          title="Transaction volume is unavailable"
          description="The transaction service did not answer this request."
          retry-label="Try again"
          @retry="loadOverview(true)"
        />
      </div>

      <ChartCard
        v-if="!transactionStatsMissing"
        class="min-w-0"
        type="bar"
        title="Successful vs failed"
        :subtitle="hourlySubtitle"
        :labels="hourlyLabels"
        :datasets="hourlyDatasets"
        :money-format="false"
        :height="250"
        :loading="overviewLoading"
        empty-message="No transactions settled in this window."
      >
        <template #actions>
          <div v-if="transactionStats" class="flex flex-wrap items-center gap-2">
            <span class="fin-chip bg-success-light text-success-dark">
              {{ transactionStats.completedToday.toLocaleString('en-US') }} settled
            </span>
            <span class="fin-chip bg-danger-light text-danger-dark">
              {{ transactionStats.failedToday.toLocaleString('en-US') }} failed
            </span>
          </div>
        </template>
      </ChartCard>

      <div v-else-if="transactionStatsMissing" class="fin-card p-4 sm:p-5">
        <ErrorState
          compact
          title="Successful vs failed is unavailable"
          description="The transaction service did not answer this request."
          retry-label="Try again"
          @retry="loadOverview(true)"
        />
      </div>

      <ChartCard
        v-if="!alertStatsMissing"
        class="min-w-0"
        type="line"
        title="Fraud alerts raised"
        subtitle="Alerts created per day, last 30 days"
        :labels="dailyAlertLabels"
        :datasets="dailyAlertDatasets"
        :money-format="false"
        :height="250"
        :loading="fraudLoading"
        empty-message="No alerts were raised in this period."
      />

      <div v-else class="fin-card p-4 sm:p-5">
        <ErrorState
          compact
          title="The alert trend is unavailable"
          :description="fraudError ?? 'Fraud statistics did not load.'"
          retry-label="Try again"
          @retry="loadFraud(true)"
        />
      </div>

<ChartCard
        v-if="userStats"
        class="min-w-0"
        type="line"
        title="User growth"
        subtitle="Registered users per month, last 12 months"
        :labels="userGrowthLabels"
        :datasets="userGrowthDatasets"
        :money-format="false"
        :height="250"
        :loading="overviewLoading"
        empty-message="No user registrations in this period."
      />

<div v-else class="fin-card p-4 sm:p-5">
        <Skeleton v-if="overviewLoading" variant="block" :rows="3" />
        <ErrorState
          v-else
          compact
          title="User growth is unavailable"
          description="The user service did not answer this request."
          retry-label="Try again"
          @retry="loadOverview(true)"
        />
      </div>
    </div>

    <section class="fin-card p-4 sm:p-5" aria-labelledby="admin-risky-heading">
      <header class="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div class="min-w-0">
          <h2 id="admin-risky-heading" class="text-headline text-ink">Highest-risk open alerts</h2>
          <p class="mt-0.5 text-caption text-ink-muted">
            Unresolved alerts, highest risk score first. Held funds stay held until an operator decides.
          </p>
        </div>
<BaseButton variant="secondary" size="sm" @click="goToFraud">
          View all alerts
          <template #trailing>
            <ArrowRight :size="15" aria-hidden="true" />
          </template>
        </BaseButton>
      </header>

      <div v-if="fraudLoading && !fraudStore.alerts.length" class="py-2" aria-busy="true">
        <Skeleton variant="block" :rows="2" />
        <span class="sr-only">Loading the risk queue</span>
      </div>

      <ErrorState
        v-else-if="fraudError && !fraudStore.alerts.length"
        compact
        title="We could not load the risk queue"
        :description="fraudError"
        retry-label="Try again"
        @retry="loadFraud(true)"
      />

      <EmptyState
        v-else-if="!riskyAlerts.length"
        compact
        title="Nothing in the risk queue"
        description="Every alert the fraud service has raised has been reviewed. New alerts appear here the moment a transaction is scored."
        :icon="ShieldAlert"
      />

      <ul v-else class="divide-y divide-border/70">
        <li v-for="alert in riskyAlerts" :key="alert.id">
          <button
            type="button"
            class="flex w-full flex-col gap-2 py-3.5 text-left transition-colors hover:bg-surface-sunken sm:flex-row sm:items-center sm:gap-4"
            @click="goToAlert(alert.id)"
          >
            <span class="flex shrink-0 items-center gap-2">
              <RiskBadge :level="alert.riskLevel" :score="alert.riskScore" size="sm" />
              <StatusBadge :status="alert.status" size="sm" />
            </span>
            <span class="min-w-0 flex-1">
              <span class="block font-mono text-[0.8125rem] font-medium text-ink">
                {{ alert.reference }}
              </span>
              <span class="mt-0.5 block truncate text-caption text-ink-subtle">
                {{ alert.senderAccountNumber }} · {{ alert.reasons[0] ?? 'No reason recorded' }}
              </span>
            </span>
            <span class="shrink-0 text-right">
              <span class="fin-amount block text-[0.9375rem] text-ink">
                {{ formatMoney(alert.amount, alert.currency) }}
              </span>
              <span class="mt-0.5 block text-caption text-ink-subtle">
                {{ formatRelative(alert.createdAt) }}
              </span>
            </span>
          </button>
        </li>
      </ul>
    </section>

    <div class="grid gap-4 lg:grid-cols-5 lg:gap-5">
      <section class="fin-card min-w-0 p-4 sm:p-5 lg:col-span-3" aria-labelledby="admin-audit-heading">
        <header class="mb-3 flex flex-wrap items-center justify-between gap-3">
          <h2 id="admin-audit-heading" class="text-headline text-ink">Recent audit activity</h2>
          <BaseButton variant="ghost" size="sm" @click="router.push({ name: 'admin-audit' })">
            Full audit trail
          </BaseButton>
        </header>

        <div v-if="overviewLoading && !adminStore.recentAudit.length" class="py-2" aria-busy="true">
          <Skeleton variant="block" :rows="3" />
          <span class="sr-only">Loading recent audit activity</span>
        </div>

        <EmptyState
          v-else-if="!adminStore.recentAudit.length"
          compact
          title="No audit entries yet"
          description="Every Finova service writes an audit entry as it happens. Entries will appear here as soon as the platform is used."
          :icon="FileSearch"
        />

<ul v-else class="divide-y divide-border/70">
          <li v-for="entry in adminStore.recentAudit" :key="entry.id">
            <AuditEntryRow :entry="entry" show-user show-resource />
          </li>
        </ul>
      </section>

      <section class="fin-card p-4 sm:p-5 lg:col-span-2" aria-labelledby="admin-actions-heading">
        <h2 id="admin-actions-heading" class="text-headline text-ink">Quick actions</h2>
        <p class="mt-0.5 text-caption text-ink-muted">
          What an administrator opens this console for.
        </p>

        <div class="mt-4 space-y-3">
          <button
            type="button"
            class="flex w-full items-start gap-3 rounded-md border border-border p-3.5 text-left transition-colors hover:border-border-strong hover:bg-surface-sunken"
            @click="goToFraud"
          >
            <span
              class="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-danger-light text-danger"
              aria-hidden="true"
            >
              <ShieldAlert :size="17" />
            </span>
            <span class="min-w-0">
              <span class="block text-[0.9375rem] font-semibold text-ink">Review fraud alerts</span>
<span class="mt-0.5 block text-caption text-ink-muted">
                {{ openAlertSummary }}
              </span>
            </span>
          </button>

          <button
            type="button"
            class="flex w-full items-start gap-3 rounded-md border border-border p-3.5 text-left transition-colors hover:border-border-strong hover:bg-surface-sunken"
            @click="goToTransactions"
          >
            <span
              class="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-primary-soft text-primary"
              aria-hidden="true"
            >
              <ArrowLeftRight :size="17" />
            </span>
            <span class="min-w-0">
              <span class="block text-[0.9375rem] font-semibold text-ink">View transactions</span>
              <span class="mt-0.5 block text-caption text-ink-muted">
                Search every movement recorded across the platform
              </span>
            </span>
          </button>

          <button
            type="button"
            class="flex w-full items-start gap-3 rounded-md border border-border p-3.5 text-left transition-colors hover:border-border-strong hover:bg-surface-sunken"
            @click="router.push({ name: 'admin-users' })"
          >
            <span
              class="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-primary-soft text-primary"
              aria-hidden="true"
            >
              <UserPlus :size="17" />
            </span>
            <span class="min-w-0">
              <span class="block text-[0.9375rem] font-semibold text-ink">Manage users</span>
<span class="mt-0.5 block text-caption text-ink-muted">
                {{ userSummary }}
              </span>
            </span>
          </button>
        </div>
      </section>
    </div>
  </div>
</template>