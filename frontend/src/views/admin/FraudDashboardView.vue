<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  AlertOctagon,
  Ban,
  CheckCircle2,
  RefreshCw,
  ShieldAlert,
  ShieldCheck,
  Users,
} from 'lucide-vue-next'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseSelect from '@/components/ui/BaseSelect.vue'
import ChartCard from '@/components/charts/ChartCard.vue'
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue'
import DateRangePicker from '@/components/ui/DateRangePicker.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import FilterBar from '@/components/ui/FilterBar.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import Pagination from '@/components/ui/Pagination.vue'
import RiskBadge from '@/components/ui/RiskBadge.vue'
import RiskMeter from '@/components/ui/RiskMeter.vue'
import SearchInput from '@/components/ui/SearchInput.vue'
import Skeleton from '@/components/ui/Skeleton.vue'
import StatCard from '@/components/ui/StatCard.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import { useFraudStore } from '@/stores/fraudStore'
import { useToastStore } from '@/stores/toastStore'
import { formatDateTime, formatMoney, formatRelative, maskAccountNumber } from '@/utils/format'
import type { FraudAlert, RiskLevel } from '@/types'

const CHART = {
  high: '#DC2626',
  medium: '#B45309',
  low: '#16A34A',
  alerts: '#25527D',
} as const

const RISK_OPTIONS = [
  { value: 'HIGH', label: 'High risk (70 and above)' },
  { value: 'MEDIUM', label: 'Medium risk (40–69)' },
  { value: 'LOW', label: 'Low risk (below 40)' },
]

const STATUS_OPTIONS = [
  { value: 'OPEN', label: 'Open' },
  { value: 'UNDER_REVIEW', label: 'Under review' },
  { value: 'SAFE', label: 'Marked safe' },
  { value: 'CONFIRMED', label: 'Confirmed fraud' },
]

const TYPING_PHRASE = 'BLOCK'

const router = useRouter()
const fraudStore = useFraudStore()
const toast = useToastStore()

const searchText = ref('')
const filters = reactive({
  search: '',
  riskLevel: '' as RiskLevel | '',
  status: '',
  from: '',
  to: '',
})

const page = ref(0)
const pageSize = ref(12)
const listError = ref<string | null>(null)
const statsError = ref<string | null>(null)
const lastUpdated = ref<string | null>(null)
const refreshing = ref(false)

const confirmOpen = ref(false)
const confirmKind = ref<'safe' | 'block'>('safe')
const target = ref<FraudAlert | null>(null)

const alerts = computed(() => fraudStore.alerts)
const stats = computed(() => fraudStore.stats)
const loading = computed(() => fraudStore.loading)
const acting = computed(() => fraudStore.acting)

const activeFilterCount = computed(
  () => [filters.search, filters.riskLevel, filters.status, filters.from, filters.to].filter((value) => String(value).trim().length > 0).length,
)

const riskLabels = computed(() => {
  const distribution = stats.value?.riskDistribution ?? {}
  return ['HIGH', 'MEDIUM', 'LOW'].filter((level) => (distribution[level] ?? 0) > 0)
})
const riskDatasets = computed(() =>
  ['HIGH', 'MEDIUM', 'LOW']
    .filter((level) => (stats.value?.riskDistribution?.[level] ?? 0) > 0)
    .map((level) => ({
      label: level === 'HIGH' ? 'High' : level === 'MEDIUM' ? 'Medium' : 'Low',
      data: [stats.value?.riskDistribution?.[level] ?? 0],
      color: level === 'HIGH' ? CHART.high : level === 'MEDIUM' ? CHART.medium : CHART.low,
    })),
)
const riskLegend = computed(() =>
  (['HIGH', 'MEDIUM', 'LOW'] as const).map((level) => ({
    level,
    count: stats.value?.riskDistribution?.[level] ?? 0,
  })),
)

const alertDays = computed(() => stats.value?.dailyAlerts ?? [])
const alertDayLabels = computed(() => alertDays.value.map((point) => point.label))
const alertDayDatasets = computed(() => [
  { label: 'Alerts raised', data: alertDays.value.map((point) => point.count), color: CHART.alerts },
])

const riskyAccounts = computed(() => (stats.value?.topRiskyAccounts ?? []).slice(0, 5))
const riskiestScore = computed(() =>
  Math.max(1, ...riskyAccounts.value.map((account) => account.maxRiskScore)),
)

async function loadAlerts(force = false): Promise<void> {
  listError.value = null
  try {
    await fraudStore.fetchAlerts(
      {
        search: filters.search || undefined,
        riskLevel: filters.riskLevel || undefined,
        status: filters.status || undefined,
        from: filters.from ? new Date(`${filters.from}T00:00:00`).toISOString() : undefined,
        to: filters.to ? new Date(`${filters.to}T23:59:59`).toISOString() : undefined,
        page: page.value,
        size: pageSize.value,
      },
      force,
    )
    lastUpdated.value = new Date().toISOString()
  } catch (cause) {
    listError.value = cause instanceof Error ? cause.message : 'We could not load fraud alerts.'
  }
}

async function loadStats(force = false): Promise<void> {
  statsError.value = null
  const result = await fraudStore.fetchStats(force)
  if (!result) {
    statsError.value = fraudStore.error ?? 'Fraud statistics are unavailable.'
  }
}

async function refreshAll(): Promise<void> {
  refreshing.value = true
  try {
    await Promise.all([loadAlerts(true), loadStats(true)])
  } finally {
    refreshing.value = false
  }
}

function reload(): void {
  page.value = 0
  void loadAlerts(true)
}

function onSearch(value: string): void {
  if (filters.search === value) return
  filters.search = value
  reload()
}

function onReset(): void {
  searchText.value = ''
  filters.search = ''
  filters.riskLevel = ''
  filters.status = ''
  filters.from = ''
  filters.to = ''
  reload()
}

function onPageChange(next: number): void {
  page.value = next
  void loadAlerts(true)
}

function onSizeChange(next: number): void {
  pageSize.value = next
  page.value = 0
  void loadAlerts(true)
}

function openAlert(alert: FraudAlert): void {
  void router.push({ name: 'admin-fraud-detail', params: { id: alert.id } })
}

function askSafe(alert: FraudAlert): void {
  target.value = alert
  confirmKind.value = 'safe'
  confirmOpen.value = true
}

function askBlock(alert: FraudAlert): void {
  target.value = alert
  confirmKind.value = 'block'
  confirmOpen.value = true
}

const confirmTitle = computed(() =>
  confirmKind.value === 'safe' ? 'Mark this alert safe' : 'Block the sending account',
)

const confirmMessage = computed(() => {
  const alert = target.value
  if (!alert) return ''
  return confirmKind.value === 'safe'
    ? `Mark ${alert.reference} as safe and release the held funds?`
    : `Block account ${maskAccountNumber(alert.senderAccountNumber)} because of ${alert.reference}?`
})

const confirmDetail = computed(() => {
  const alert = target.value
  const amount = alert ? formatMoney(alert.amount, alert.currency) : ''
  if (confirmKind.value === 'safe') {
    return `The transaction is no longer held: ${amount} settles and the receiver is credited. If the transfer really was fraudulent you will have to recover that money separately — this decision cannot be undone from the console.`
  }
  return `This locks a real customer out of the account immediately and drops its available balance to zero. The held transfer stays frozen and the alert is confirmed rather than resolved. Blocking the wrong account takes the customer's own money away from them with no automatic reversal.`
})

async function runAction(): Promise<void> {
  const alert = target.value
  if (!alert || acting.value) return
  try {
    const updated = await fraudStore.runAction(alert.id, confirmKind.value === 'safe' ? 'safe' : 'block')
    confirmOpen.value = false
    target.value = updated
    toast.success(
      confirmKind.value === 'safe' ? 'Alert marked safe' : 'Account blocked',
      confirmKind.value === 'safe'
        ? `${formatMoney(updated.amount, updated.currency)} released for settlement.`
        : `${maskAccountNumber(updated.senderAccountNumber)} is blocked and the alert is confirmed.`,
    )
    await Promise.all([loadStats(true), loadAlerts(true)])
  } catch (cause) {
    toast.fromError(
      cause,
      confirmKind.value === 'safe' ? 'We could not mark this alert safe' : 'We could not block this account',
    )
  }
}

watch(() => [filters.riskLevel, filters.status], () => reload())
watch(() => [filters.from, filters.to], () => reload())

onMounted(() => {
  void Promise.all([loadAlerts(), loadStats()])
})
</script>

<template>
  <div class="space-y-5 pb-4">
    <PageHeader
      eyebrow="Risk operations"
      title="Fraud &amp; Risk Monitoring"
      description="Monitor suspicious financial activity."
    >
      <template #actions>
        <p class="text-caption text-ink-subtle">
          Updated {{ lastUpdated ? formatRelative(lastUpdated) : 'never' }}
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

    <!-- Summary -->
    <div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <StatCard
        v-for="card in [
          { key: 'open', label: 'Open Alerts', value: stats?.openAlerts ?? null, tone: 'neutral' as const, hint: 'Not yet reviewed' },
          { key: 'high', label: 'High Risk', value: stats?.highRisk ?? null, tone: 'danger' as const, hint: 'Score 70 and above' },
          { key: 'medium', label: 'Medium Risk', value: stats?.mediumRisk ?? null, tone: 'warning' as const, hint: 'Score 40 to 69' },
          { key: 'resolved', label: 'Resolved Today', value: stats?.resolvedToday ?? null, tone: 'success' as const, hint: 'Safe or confirmed today' },
        ]"
        :key="card.key"
        :label="card.label"
        :value="card.value === null ? '—' : card.value.toLocaleString('en-US')"
        :tone="card.tone"
        :hint="card.hint"
        :loading="fraudStore.loading && !stats && !statsError"
      >
        <template #icon>
          <AlertOctagon v-if="card.key === 'open'" :size="17" />
          <ShieldAlert v-else-if="card.key === 'high'" :size="17" />
          <ShieldCheck v-else-if="card.key === 'medium'" :size="17" />
          <CheckCircle2 v-else :size="17" />
        </template>
      </StatCard>
    </div>

    <div
      v-if="statsError"
      class="rounded-md bg-warning-light px-3 py-2.5 text-[0.875rem] text-warning-dark"
      role="alert"
    >
      {{ statsError }} The alert list below is unaffected.
      <button type="button" class="ml-2 font-semibold underline" @click="loadStats(true)">Try again</button>
    </div>

    <!-- Charts -->
    <div class="grid gap-4 lg:grid-cols-2 lg:gap-5">
      <ChartCard
        class="min-w-0"
        v-if="stats"
        type="doughnut"
        title="Risk distribution"
        subtitle="Every assessment the fraud service has scored"
        :labels="riskLabels"
        :datasets="riskDatasets"
        :money-format="false"
        :height="240"
        empty-message="No assessments recorded yet."
      >
        <template #actions>
          <ul class="space-y-1 text-right">
            <li v-for="row in riskLegend" :key="row.level" class="text-caption text-ink-muted">
              <span class="font-semibold text-ink">{{ row.count.toLocaleString('en-US') }}</span>
              {{ row.level === 'HIGH' ? 'high' : row.level === 'MEDIUM' ? 'medium' : 'low' }}
            </li>
          </ul>
        </template>
      </ChartCard>

      <div v-else class="fin-card p-4 sm:p-5">
        <Skeleton v-if="loading" variant="block" :rows="3" />
        <ErrorState
          v-else
          compact
          title="Risk distribution unavailable"
          :description="statsError ?? 'The fraud service did not return risk statistics.'"
          retry-label="Try again"
          @retry="loadStats(true)"
        />
      </div>

      <ChartCard

        class="min-w-0"
        v-if="stats"
        type="bar"
        title="Alerts over time"
        subtitle="Alerts raised per day, last 30 days"
        :labels="alertDayLabels"
        :datasets="alertDayDatasets"
        :money-format="false"
        :height="240"
        empty-message="No alerts raised in this period."
      >
        <template #actions>
          <p v-if="stats" class="text-caption text-ink-subtle">
            {{ stats.confirmedToday.toLocaleString('en-US') }} confirmed fraud ·
            {{ stats.totalAssessedToday.toLocaleString('en-US') }} assessed today
          </p>
        </template>
      </ChartCard>

      <div v-else class="fin-card p-4 sm:p-5">
        <Skeleton v-if="loading" variant="block" :rows="3" />
        <ErrorState
          v-else
          compact
          title="Alert trend unavailable"
          :description="statsError ?? 'The fraud service did not return alert statistics.'"
          retry-label="Try again"
          @retry="loadStats(true)"
        />
      </div>
    </div>

    <!-- Filters -->
    <FilterBar :active-count="activeFilterCount" @reset="onReset">
      <template #search>
        <SearchInput
          v-model="searchText"
          label="fraud alerts"
          placeholder="Search reference or account number"
          :debounce-ms="400"
          @search="onSearch"
        />
      </template>

      <template #primary>
        <BaseSelect
          v-model="filters.riskLevel"
          label="Risk level"
          :options="RISK_OPTIONS"
          placeholder="All risk levels"
          class="w-full sm:w-56"
        />
      </template>

      <template #advanced>
        <BaseSelect
          v-model="filters.status"
          label="Status"
          :options="STATUS_OPTIONS"
          placeholder="All statuses"
        />
        <DateRangePicker v-model:from="filters.from" v-model:to="filters.to" label="Raised between" />
      </template>
    </FilterBar>

    <!-- Alert queue -->
    <section aria-labelledby="fraud-queue-heading">
      <div class="mb-3 flex flex-wrap items-baseline justify-between gap-2">
        <h2 id="fraud-queue-heading" class="text-headline text-ink">
          Alert queue
          <span class="ml-1 text-caption font-normal text-ink-subtle">
            (<span aria-live="polite">{{ fraudStore.totalElements.toLocaleString('en-US') }}</span> matching)
          </span>
        </h2>
        <p class="text-caption text-ink-subtle">Highest risk score first</p>
      </div>

      <div v-if="loading && alerts.length === 0 && !listError" class="grid gap-4 lg:grid-cols-2 xl:grid-cols-3" aria-busy="true">
        <div v-for="placeholder in 6" :key="placeholder" class="fin-card p-4">
          <Skeleton variant="block" :rows="4" />
        </div>
        <span class="sr-only">Loading fraud alerts</span>
      </div>

      <ErrorState
        v-else-if="listError && alerts.length === 0"
        class="fin-card"
        title="We could not load fraud alerts"
        :description="listError"
        retry-label="Reload alerts"
        @retry="loadAlerts(true)"
      />

      <div v-else-if="!alerts.length" class="fin-card">
        <EmptyState
          :icon="ShieldCheck"
          title="No alerts match these filters"
          description="Nothing in the fraud queue matches the current risk level, status, search term or date window. That is good news — reset the filters to widen the view."
          action-label="Reset filters"
          @action="onReset"
        />
      </div>

      <ul v-else class="grid gap-4 lg:grid-cols-2 xl:grid-cols-3">
        <li
          v-for="alert in alerts"
          :key="alert.id"
          class="flex min-w-0 flex-col rounded-lg border bg-surface p-4 shadow-card transition-shadow hover:shadow-raised"
          :class="alert.riskLevel === 'HIGH' ? 'border-danger/30' : 'border-border'"
        >
          <div class="flex items-start justify-between gap-3">
            <RiskBadge :level="alert.riskLevel" :score="alert.riskScore" />
            <StatusBadge :status="alert.status" size="sm" />
          </div>

          <p class="mt-3 font-mono text-[0.8125rem] font-medium text-ink">{{ alert.reference }}</p>

          <p class="mt-1 flex flex-wrap items-baseline gap-x-2">
            <span class="fin-amount text-[1.5rem] leading-none text-ink">
              {{ formatMoney(alert.amount, alert.currency) }}
            </span>
            <span class="text-caption text-ink-subtle">{{ alert.currency }}</span>
          </p>

          <p class="mt-1 font-mono text-caption text-ink-subtle">
            {{ alert.senderAccountNumber }}
          </p>

          <div class="mt-3">
            <RiskMeter :score="alert.riskScore" :level="alert.riskLevel" size="sm" />
          </div>

          <div class="mt-3 min-w-0">
            <p class="text-caption font-semibold uppercase tracking-wider text-ink-subtle">
              Why it was flagged
            </p>
            <ul class="mt-1 space-y-1">
              <li
                v-for="reason in alert.reasons"
                :key="reason"
                class="flex items-start gap-2 text-[0.875rem] leading-snug text-ink-muted"
              >
                <span class="mt-1.5 h-1 w-1 shrink-0 rounded-full bg-ink-subtle" aria-hidden="true" />
                <span>{{ reason }}</span>
              </li>
            </ul>
            <p v-if="!alert.reasons.length" class="text-[0.875rem] text-ink-subtle">
              No reason recorded on this alert.
            </p>
          </div>

          <p class="mt-3 text-caption text-ink-subtle">
            Raised
            <time :datetime="alert.createdAt" :title="formatDateTime(alert.createdAt)">
              {{ formatRelative(alert.createdAt) }}
            </time>
          </p>

          <div class="mt-3 flex flex-wrap items-center gap-2 border-t border-border pt-3">
            <BaseButton size="sm" :disabled="acting" @click="openAlert(alert)">Review</BaseButton>
            <BaseButton
              variant="secondary"
              size="sm"
              :disabled="acting || alert.status === 'SAFE' || alert.status === 'CONFIRMED'"
              @click="askSafe(alert)"
            >
              <template #icon>
                <CheckCircle2 :size="14" aria-hidden="true" />
              </template>
              Mark Safe
            </BaseButton>
            <BaseButton
              variant="danger"
              size="sm"
              :disabled="acting || alert.status === 'CONFIRMED'"
              @click="askBlock(alert)"
            >
              <template #icon>
                <Ban :size="14" aria-hidden="true" />
              </template>
              Block Account
            </BaseButton>
          </div>
        </li>
      </ul>

      <div v-if="alerts.length" class="mt-4 fin-card px-4 py-3.5">
        <Pagination
          :page="page"
          :total-pages="fraudStore.totalPages"
          :total-elements="fraudStore.totalElements"
          :page-size="pageSize"
          label="alerts"
          @change="onPageChange"
          @size-change="onSizeChange"
        />
      </div>
    </section>

    <!-- Riskiest accounts -->
    <section class="fin-card p-4 sm:p-5" aria-labelledby="fraud-risky-accounts-heading">
      <h2 id="fraud-risky-accounts-heading" class="text-headline text-ink">Top risky accounts</h2>
      <p class="mt-0.5 text-caption text-ink-muted">
        Accounts by number of alerts raised, with the highest score each one reached.
      </p>

      <div v-if="loading && !riskyAccounts.length" class="mt-3" aria-busy="true">
        <Skeleton variant="block" :rows="3" />
        <span class="sr-only">Loading the riskiest accounts</span>
      </div>

      <EmptyState
        v-else-if="!riskyAccounts.length"
        compact
        title="No account has been flagged"
        description="Once the fraud service raises alerts against an account, the busiest senders are ranked here."
        :icon="Users"
      />

      <ol v-else class="mt-4 space-y-3">
        <li v-for="(account, index) in riskyAccounts" :key="account.accountId">
          <div class="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
            <span class="flex min-w-0 items-baseline gap-2">
              <span class="text-caption font-bold tabular-nums text-ink-subtle">{{ index + 1 }}</span>
              <span class="truncate font-mono text-[0.8125rem] font-medium text-ink">
                {{ account.accountNumber }}
              </span>
            </span>
            <span class="text-caption text-ink-muted">
              {{ account.count }} alert{{ account.count === 1 ? '' : 's' }} · peak
              <span class="font-semibold text-ink">{{ account.maxRiskScore }}/100</span>
            </span>
          </div>
          <div class="mt-1.5">
            <RiskMeter
              :score="account.maxRiskScore"
              size="sm"
              :label="`Peak risk score for account ${account.accountNumber}`"
            />
          </div>
          <span class="sr-only">Bar length relative to the riskiest account on the platform.</span>
          <span class="sr-only">Scale maximum {{ riskiestScore }} out of 100.</span>
        </li>
      </ol>
    </section>

    <ConfirmDialog
      :open="confirmOpen"
      :title="confirmTitle"
      :message="confirmMessage"
      :detail="confirmDetail"
      :confirm-label="confirmKind === 'safe' ? 'Mark safe' : 'Block account'"
      :tone="confirmKind === 'safe' ? 'warning' : 'danger'"
      :require-typing="confirmKind === 'block'"
      :typing-phrase="TYPING_PHRASE"
      :loading="acting"
      @confirm="runAction"
      @cancel="confirmOpen = false"
    />
  </div>
</template>