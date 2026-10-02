<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDownLeft, ArrowLeft, ArrowUpRight, ShieldQuestion } from 'lucide-vue-next'
import PageHeader from '@/components/ui/PageHeader.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import DetailRow from '@/components/ui/DetailRow.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import RiskBadge from '@/components/ui/RiskBadge.vue'
import Skeleton from '@/components/ui/Skeleton.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import TimelineList from '@/components/ui/TimelineList.vue'
import { useAccountStore } from '@/stores/accountStore'
import { useTransactionStore } from '@/stores/transactionStore'
import { transactionApi } from '@/api'
import { AppError } from '@/utils/errors'
import { formatDateTime, formatMoney } from '@/utils/format'
import { useDisplayPreferences } from '@/composables/useDisplayPreferences'
import type { TimelineStep, Transaction } from '@/types'

const route = useRoute()
const router = useRouter()
const accountStore = useAccountStore()
const transactionStore = useTransactionStore()
const { formatDisplayDateTime } = useDisplayPreferences()

const loading = ref(true)
const error = ref<string | null>(null)
const offline = ref(false)
const notFound = ref(false)
const transaction = ref<Transaction | null>(null)
const steps = ref<TimelineStep[]>([])

const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : ''))

const ownsSender = computed(
  () => Boolean(transaction.value && accountStore.byId[transaction.value.senderAccountId]),
)
const ownsReceiver = computed(
  () => Boolean(transaction.value && accountStore.byId[transaction.value.receiverAccountId]),
)

/** Direction is derived from ownership, never guessed from the description. */
const direction = computed<'in' | 'out' | 'flat'>(() => {
  if (!transaction.value) return 'flat'
  if (ownsSender.value) return 'out'
  if (ownsReceiver.value) return 'in'
  return 'flat'
})

const directionLabel = computed(() => {
  if (direction.value === 'in') return 'Money received'
  if (direction.value === 'out') return 'Money sent'
  return transaction.value?.type === 'DEPOSIT' ? 'Deposit' : transaction.value?.type === 'WITHDRAWAL' ? 'Withdrawal' : 'Transfer'
})

const signedAmount = computed(() => {
  if (!transaction.value) return '—'
  const amount = formatMoney(transaction.value.amount, transaction.value.currency)
  if (direction.value === 'in') return `+${amount}`
  if (direction.value === 'out') return `−${amount}`
  return amount
})

const riskReasons = computed(() => transaction.value?.riskReasons ?? [])

async function load(force = false): Promise<void> {
  if (!id.value) {
    loading.value = false
    notFound.value = true
    return
  }
  loading.value = true
  error.value = null
  notFound.value = false
  try {
    transaction.value = await transactionStore.fetchOne(id.value, force)
    steps.value = (await transactionApi.timeline(id.value)).steps
  } catch (cause) {
    transaction.value = null
    steps.value = []
    if (cause instanceof AppError && (cause.status === 404 || cause.code === 'TRANSACTION_NOT_FOUND')) {
      notFound.value = true
    } else {
      error.value = cause instanceof Error ? cause.message : 'We could not load this transaction.'
      offline.value = cause instanceof AppError ? cause.isNetworkError : false
    }
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  // Ownership decides the sign of the amount, so the accounts have to be known.
  await accountStore.fetchAll().catch(() => undefined)
  await load(true)
})
</script>

<template>
  <div class="space-y-5 pb-4">
    <BaseButton variant="ghost" size="sm" @click="router.push({ name: 'transactions' })">
      <template #icon>
        <ArrowLeft :size="15" aria-hidden="true" />
      </template>
      Back to transactions
    </BaseButton>

    <div v-if="loading" class="fin-card space-y-4 p-5" aria-busy="true">
      <Skeleton variant="block" :rows="4" />
      <span class="sr-only">Loading the transaction</span>
    </div>

    <ErrorState
      v-else-if="error"
      class="fin-card"
      title="We could not load this transaction"
      :description="error"
      :offline="offline"
      retry-label="Try again"
      @retry="load(true)"
    />

    <div v-else-if="notFound || !transaction" class="fin-card">
      <EmptyState
        title="Transaction not found"
        description="We could not find a transaction with this reference. It may belong to another account, or the link may be out of date."
        action-label="Back to transactions"
        @action="router.push({ name: 'transactions' })"
      />
    </div>

    <template v-else>
      <PageHeader title="Transaction details" :description="directionLabel">
        <template #actions>
          <div class="flex flex-wrap items-center gap-2">
            <StatusBadge :status="transaction.status" />
            <RiskBadge v-if="transaction.riskLevel" :level="transaction.riskLevel" :score="transaction.riskScore" />
          </div>
        </template>
      </PageHeader>

      <p class="-mt-3 flex flex-wrap items-center gap-x-2 gap-y-1 font-mono text-[0.8125rem] text-ink-subtle">
        <span>{{ transaction.reference }}</span>
        <span aria-hidden="true">·</span>
        <span>{{ formatDateTime(transaction.createdAt) }}</span>
      </p>

      <div class="grid gap-5 lg:grid-cols-[minmax(0,1fr)_20rem] lg:items-start">
        <div class="space-y-5">
          <!-- Amount -->
          <section class="fin-card p-5 sm:p-6">
            <p class="text-caption font-semibold uppercase tracking-wider text-ink-subtle">
              {{ directionLabel }}
            </p>
            <p
              class="fin-amount mt-1.5 text-[2.25rem] sm:text-display"
              :class="transaction.status === 'FAILED' || transaction.status === 'REJECTED' ? 'text-ink-subtle' : 'text-ink'"
            >
              {{ signedAmount }}
            </p>
            <p v-if="transaction.description" class="mt-2 text-[0.9375rem] text-ink-muted">
              {{ transaction.description }}
            </p>
            <p
              v-if="transaction.failureReason"
              class="mt-3 flex items-start gap-2 rounded-md bg-danger-light px-3 py-2.5 text-[0.875rem] text-danger-dark"
              role="alert"
            >
              {{ transaction.failureReason }}
            </p>
          </section>

          <!-- From / to -->
          <section class="fin-card p-5">
            <h2 class="text-headline text-ink">Movement</h2>
            <div class="mt-3 grid gap-3 sm:grid-cols-[1fr_auto_1fr] sm:items-center">
              <div class="rounded-md border border-border bg-surface-sunken p-3.5">
                <p class="text-caption font-semibold uppercase tracking-wider text-ink-subtle">From</p>
                <p class="mt-1 font-mono text-[0.9375rem] text-ink">{{ transaction.senderAccountNumber }}</p>
                <p class="mt-0.5 truncate text-caption text-ink-muted">{{ transaction.senderDisplay }}</p>
                <p v-if="ownsSender" class="mt-1 text-caption text-ink-subtle">One of your accounts</p>
              </div>

              <div class="flex items-center justify-center gap-2 text-ink-subtle" aria-hidden="true">
                <ArrowUpRight v-if="direction === 'out'" :size="18" />
                <ArrowDownLeft v-else :size="18" />
              </div>

              <div class="rounded-md border border-border bg-surface-sunken p-3.5">
                <p class="text-caption font-semibold uppercase tracking-wider text-ink-subtle">To</p>
                <p class="mt-1 font-mono text-[0.9375rem] text-ink">{{ transaction.receiverAccountNumber }}</p>
                <p class="mt-0.5 truncate text-caption text-ink-muted">{{ transaction.receiverDisplay }}</p>
                <p v-if="ownsReceiver" class="mt-1 text-caption text-ink-subtle">One of your accounts</p>
              </div>
            </div>

            <dl class="mt-3 divide-y divide-border border-t border-border">
              <DetailRow label="Type" :value="transaction.type" />
              <DetailRow
                label="Fee"
                :value="formatMoney(transaction.fee, transaction.currency)"
                hint="Finova charges no transfer fee."
              />
              <DetailRow
                label="Total"
                :value="formatMoney(transaction.totalAmount, transaction.currency)"
              />
              <DetailRow label="Created" :value="formatDisplayDateTime(transaction.createdAt)" />
              <DetailRow
                v-if="transaction.completedAt"
                label="Completed"
                :value="formatDisplayDateTime(transaction.completedAt)"
              />
              <DetailRow
                v-if="transaction.correlationId"
                label="Trace reference"
                :value="transaction.correlationId"
                mono
                hint="Quote this to Finova support if you need to trace the transfer."
              />
            </dl>
          </section>

          <!-- Why was this flagged? -->
          <section
            v-if="riskReasons.length > 0"
            class="rounded-lg border p-4 sm:p-5"
            :class="transaction.riskLevel === 'HIGH' ? 'border-danger/25 bg-danger-light' : transaction.riskLevel === 'MEDIUM' ? 'border-warning/25 bg-warning-light' : 'border-border bg-surface'"
          >
            <div class="flex flex-wrap items-center justify-between gap-2">
              <h2 class="flex items-center gap-2 text-headline text-ink">
                <ShieldQuestion :size="17" aria-hidden="true" />
                Why was this flagged?
              </h2>
              <RiskBadge
                v-if="transaction.riskLevel"
                :level="transaction.riskLevel"
                :score="transaction.riskScore"
                size="sm"
              />
            </div>
            <p class="mt-1.5 text-[0.875rem] leading-relaxed text-ink-muted">
              Finova's risk engine scores every transfer. These are the reasons recorded for this one:
            </p>
            <ul class="mt-3 space-y-1.5">
              <li
                v-for="reason in riskReasons"
                :key="reason"
                class="flex items-start gap-2 text-[0.875rem] text-ink"
              >
                <span class="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-ink-subtle" aria-hidden="true" />
                <span>{{ reason }}</span>
              </li>
            </ul>
          </section>
        </div>

        <!-- Timeline -->
        <aside class="space-y-4 lg:sticky lg:top-20">
          <section class="fin-card p-4 sm:p-5" aria-labelledby="timeline-heading">
            <h2 id="timeline-heading" class="text-headline text-ink">Event timeline</h2>
            <p class="mt-0.5 text-caption text-ink-muted">
              Created → Validated → Fraud checked → Settled → Notified
            </p>

            <div class="mt-4">
              <TimelineList
                v-if="steps.length > 0"
                :steps="steps"
                aria-label="Transaction event timeline"
                :format-time="formatDisplayDateTime"
              />
              <Skeleton v-else variant="block" :rows="3" />
            </div>
          </section>
        </aside>
      </div>
    </template>
  </div>
</template>
