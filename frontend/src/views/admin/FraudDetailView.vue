<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeft,
  Ban,
  CheckCircle2,
  CircleDot,
  RefreshCw,
  ShieldAlert,
  UserCheck,
} from 'lucide-vue-next'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseInput from '@/components/ui/BaseInput.vue'
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue'
import DetailRow from '@/components/ui/DetailRow.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import RiskBadge from '@/components/ui/RiskBadge.vue'
import RiskMeter from '@/components/ui/RiskMeter.vue'
import Skeleton from '@/components/ui/Skeleton.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import { useFraudStore } from '@/stores/fraudStore'
import { useToastStore } from '@/stores/toastStore'
import { formatDateTime, formatMoney, formatRelative, maskAccountNumber } from '@/utils/format'
import type { FraudAlert, FraudTimelineStep } from '@/types'

const TYPING_PHRASE = 'BLOCK'

type Action = 'review' | 'safe' | 'confirm' | 'block'

const route = useRoute()
const router = useRouter()
const fraudStore = useFraudStore()
const toast = useToastStore()

const alertId = computed(() => (typeof route.params.id === 'string' ? route.params.id : ''))

const loading = ref(true)
const error = ref<string | null>(null)
const note = ref('')
const confirmOpen = ref(false)
const action = ref<Action>('review')
const acting = ref(false)

const alert = computed<FraudAlert | null>(() => fraudStore.selected)
const stats = computed(() => fraudStore.stats)

const timeline = computed<FraudTimelineStep[]>(() => alert.value?.timeline ?? [])

/** The first steps describe scoring; anything an operator did shows a tick. */
function stepKind(step: FraudTimelineStep, index: number): 'review' | 'tick' | 'dot' | 'ring' {
  if (/REVIEW|SAFE|CONFIRM|BLOCK/i.test(step.key)) return 'review'
  if (index === 0) return 'tick'
  if (index === timeline.value.length - 1) return 'ring'
  return 'dot'
}

function stepLabel(kind: ReturnType<typeof stepKind>): string {
  if (kind === 'review') return 'Operator action'
  if (kind === 'tick') return 'Started'
  if (kind === 'ring') return 'Final step'
  return 'Processing step'
}

/** Node colour mirrors the operator's decision: an intervention reads as danger. */
function nodeClass(kind: ReturnType<typeof stepKind>): string {
  if (kind === 'review') {
    return alert.value?.status === 'CONFIRMED'
      ? 'bg-danger text-white'
      : 'bg-primary text-white'
  }
  if (kind === 'tick') return 'bg-primary text-white'
  if (kind === 'ring') return 'bg-accent-light text-accent-dark ring-2 ring-accent'
  return 'bg-surface text-primary ring-2 ring-primary'
}

const confirmTitle = computed(() => {
  if (action.value === 'review') return 'Start reviewing this alert'
  if (action.value === 'safe') return 'Mark this alert safe'
  if (action.value === 'confirm') return 'Confirm this transfer as fraud'
  return 'Block the sending account'
})

const confirmMessage = computed(() => {
  const reference = alert.value?.reference ?? 'this alert'
  if (action.value === 'review') return `Take ownership of ${reference}?`
  if (action.value === 'safe') return `Mark ${reference} as safe and release the held funds?`
  if (action.value === 'confirm') return `Confirm ${reference} as fraudulent?`
  return `Block account ${maskAccountNumber(alert.value?.senderAccountNumber)} because of ${reference}?`
})

const confirmDetail = computed(() => {
  const current = alert.value
  const amount = current ? formatMoney(current.amount, current.currency) : ''
  if (action.value === 'review') {
    return 'The alert moves to “Under review” and is attributed to you, so colleagues can see it is being worked on. Nothing about the money changes.'
  }
  if (action.value === 'safe') {
    return `The hold is lifted and ${amount} settles to the receiver. This is the point of no return: the console cannot put a settled transfer back, so a later recovery would have to be made manually.`
  }
  if (action.value === 'confirm') {
    return `The alert is recorded as fraud and ${amount} STAYS HELD — Finova does not automatically reverse a confirmed transfer, so the money remains frozen pending a manual recovery or chargeback. Confirm this only when you intend to pursue recovery.`
  }
  return `The sender's account ${maskAccountNumber(current?.senderAccountNumber)} is blocked immediately and its available balance drops to zero. The customer loses access to their own money with no automatic reversal, and the alert is confirmed rather than resolved.`
})

const confirmLabel = computed(() => {
  if (action.value === 'review') return 'Start review'
  if (action.value === 'safe') return 'Mark safe'
  if (action.value === 'confirm') return 'Confirm fraud'
  return 'Block account'
})

async function load(): Promise<void> {
  if (!alertId.value) {
    error.value = 'This alert link is missing its identifier.'
    loading.value = false
    return
  }
  loading.value = true
  error.value = null
  try {
    await fraudStore.fetchOne(alertId.value)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'We could not load this fraud alert.'
  } finally {
    loading.value = false
  }
}

async function loadStats(): Promise<void> {
  // A fresh summary keeps the header counts honest after a decision.
  await fraudStore.fetchStats(true)
}

function ask(next: Action): void {
  action.value = next
  note.value = ''
  confirmOpen.value = true
}

async function runAction(): Promise<void> {
  const current = alert.value
  if (!current || acting.value) return
  acting.value = true
  try {
    const updated = await fraudStore.runAction(current.id, action.value, note.value.trim() || undefined)
    confirmOpen.value = false
    note.value = ''
    await Promise.all([load(), loadStats()])
    toast.success(
      action.value === 'review'
        ? 'Review started'
        : action.value === 'safe'
          ? 'Alert marked safe'
          : action.value === 'confirm'
            ? 'Fraud confirmed'
            : 'Account blocked',
      action.value === 'review'
        ? `${updated.reference} is now under your review. Funds stay held.`
        : action.value === 'safe'
          ? `${formatMoney(updated.amount, updated.currency)} released for settlement.`
          : action.value === 'confirm'
            ? `${formatMoney(updated.amount, updated.currency)} stays held for manual recovery. Finova does not reverse it automatically.`
            : `${maskAccountNumber(updated.senderAccountNumber)} is blocked and the alert is confirmed.`,
    )
  } catch (cause) {
    toast.fromError(cause, 'We could not record that decision on this alert')
  } finally {
    acting.value = false
  }
}

watch(alertId, () => void load())

onMounted(() => {
  void Promise.all([load(), loadStats()])
})
</script>

<template>
  <div class="space-y-5 pb-4">
    <BaseButton variant="ghost" size="sm" @click="router.push({ name: 'admin-fraud' })">
      <template #icon>
        <ArrowLeft :size="15" aria-hidden="true" />
      </template>
      Back to fraud &amp; risk
    </BaseButton>

    <div v-if="loading && !alert" class="space-y-5" aria-busy="true">
      <div class="fin-card p-5">
        <Skeleton variant="block" :rows="3" />
      </div>
      <div class="fin-card p-5">
        <Skeleton variant="block" :rows="5" />
      </div>
      <span class="sr-only">Loading fraud alert</span>
    </div>

    <ErrorState
      v-else-if="error && !alert"
      class="fin-card"
      title="We could not load this fraud alert"
      :description="error"
      retry-label="Reload the alert"
      @retry="load"
    />

    <template v-else-if="alert">
      <PageHeader
        eyebrow="Risk operations"
        :title="alert.reference"
        :description="`Alert raised ${formatRelative(alert.createdAt)} on account ${maskAccountNumber(alert.senderAccountNumber)}.`"
      >
        <template #actions>
          <div class="flex flex-wrap items-center gap-2">
            <RiskBadge :level="alert.riskLevel" :score="alert.riskScore" />
            <StatusBadge :status="alert.status" />
          </div>
        </template>
      </PageHeader>

      <div
        v-if="error"
        class="rounded-md bg-danger-light px-3 py-2.5 text-[0.875rem] text-danger-dark"
        role="alert"
      >
        {{ error }}
        <button type="button" class="ml-2 font-semibold underline" @click="load">Try again</button>
      </div>

      <div class="grid gap-4 lg:grid-cols-5 lg:gap-5">
        <!-- Headline -->
        <section class="fin-card p-4 sm:p-5 lg:col-span-2" aria-labelledby="fraud-amount-heading">
          <h2 id="fraud-amount-heading" class="sr-only">Amount at risk</h2>
          <p class="text-caption font-semibold uppercase tracking-wider text-ink-subtle">
            Amount under review
          </p>
          <p class="mt-1.5 flex flex-wrap items-baseline gap-x-2">
            <span class="fin-amount text-[2rem] leading-none text-ink">
              {{ formatMoney(alert.amount, alert.currency) }}
            </span>
            <span class="text-caption text-ink-subtle">{{ alert.currency }}</span>
          </p>
          <p class="mt-2 text-[0.875rem] leading-relaxed text-ink-muted">
            <template v-if="alert.status === 'SAFE'">
              This transfer was cleared and the money settled to the receiver.
            </template>
            <template v-else-if="alert.status === 'CONFIRMED'">
              This transfer was confirmed as fraud. The funds stay held — Finova does not reverse a
              confirmed transfer automatically.
            </template>
            <template v-else>
              The funds are held while this alert is unresolved. Nothing moves until an operator decides.
            </template>
          </p>

          <div class="mt-4">
            <RiskMeter :score="alert.riskScore" :level="alert.riskLevel" />
          </div>

          <dl class="mt-4 divide-y divide-border border-t border-border">
            <DetailRow label="Raised" :value="formatDateTime(alert.createdAt)" />
            <DetailRow label="Last updated" :value="formatDateTime(alert.updatedAt)" />
          </dl>
        </section>

        <!-- Timeline -->
        <section class="fin-card p-4 sm:p-5 lg:col-span-3" aria-labelledby="fraud-timeline-heading">
          <h2 id="fraud-timeline-heading" class="text-headline text-ink">Investigation timeline</h2>
          <p class="mt-0.5 text-caption text-ink-muted">
            Every step the platform recorded, from acceptance to the current decision.
          </p>

          <ol v-if="timeline.length" class="mt-4" aria-label="Fraud alert investigation timeline">
            <li v-for="(step, index) in timeline" :key="step.key" class="relative flex gap-3.5 pb-5 last:pb-0">
              <span
                v-if="index < timeline.length - 1"
                class="absolute left-[15px] top-8 h-[calc(100%-1.5rem)] w-px bg-border"
                aria-hidden="true"
              />

              <span
                class="relative z-10 flex h-8 w-8 shrink-0 items-center justify-center rounded-full"
                :class="nodeClass(stepKind(step, index))"
                aria-hidden="true"
              >
                <CheckCircle2 v-if="stepKind(step, index) === 'tick'" :size="15" />
                <CircleDot v-else-if="stepKind(step, index) === 'dot'" :size="14" />
                <ShieldAlert v-else-if="stepKind(step, index) === 'ring'" :size="15" />
                <UserCheck v-else :size="15" />
              </span>

              <div class="min-w-0 flex-1 pt-0.5">
                <div class="flex flex-wrap items-center gap-x-2 gap-y-1">
                  <p class="text-[0.9375rem] font-semibold text-ink">{{ step.label }}</p>
                  <!-- The node shape above is decorative; state is always spelled out in text. -->
                  <span class="fin-chip h-6 bg-surface-sunken text-ink-muted">
                    {{ stepLabel(stepKind(step, index)) }}
                  </span>
                </div>

                <p class="mt-1 text-[0.875rem] leading-relaxed text-ink-muted">{{ step.description }}</p>

                <p v-if="step.at" class="mt-1 text-caption tabular-nums text-ink-subtle">
                  <time :datetime="step.at">{{ formatDateTime(step.at) }}</time>
                </p>
              </div>
            </li>
          </ol>

          <p v-else class="mt-4 text-[0.875rem] text-ink-subtle">
            The fraud service did not return a timeline for this alert.
          </p>
        </section>
      </div>

      <div class="grid gap-4 lg:grid-cols-2 lg:gap-5">
        <!-- Details -->
        <section class="fin-card min-w-0 p-4 sm:p-5" aria-labelledby="fraud-details-heading">
          <h2 id="fraud-details-heading" class="text-headline text-ink">Alert details</h2>

          <dl class="mt-2 divide-y divide-border border-t border-border">
            <DetailRow label="Transaction">
              <RouterLink
                :to="{ name: 'transaction-detail', params: { id: alert.transactionId } }"
                class="font-mono text-[0.8125rem] text-accent underline underline-offset-2 hover:text-accent-dark"
              >
                {{ alert.reference }}
              </RouterLink>
            </DetailRow>
            <DetailRow label="Sending account" :value="maskAccountNumber(alert.senderAccountNumber)" mono />
            <DetailRow
              label="Receiving account"
              :value="alert.receiverAccountId ?? undefined"
              mono
              :hint="alert.receiverAccountId ? undefined : 'No receiving account recorded on this alert.'"
            />
            <DetailRow label="Sender" :value="alert.senderUserId" mono />
            <DetailRow label="Amount" :value="formatMoney(alert.amount, alert.currency)" />
            <DetailRow label="Risk score" :value="`${alert.riskScore} / 100 — ${alert.riskLevel.toLowerCase()} risk`" />
            <DetailRow label="Raised" :value="formatDateTime(alert.createdAt)" />
          </dl>

          <div class="mt-4 border-t border-border pt-4">
            <p class="fin-label">Rules triggered</p>
            <ul v-if="alert.triggeredRules.length" class="flex flex-wrap gap-1.5">
              <li v-for="rule in alert.triggeredRules" :key="rule">
                <span class="fin-chip border border-border bg-surface-sunken font-mono text-ink-muted">
                  {{ rule }}
                </span>
              </li>
            </ul>
            <p v-else class="text-[0.875rem] text-ink-subtle">
              No fraud rule fired on this transfer.
            </p>
          </div>

          <div v-if="alert.reasons.length" class="mt-4 border-t border-border pt-4">
            <p class="fin-label">Reasons recorded</p>
            <ul class="space-y-1.5">
              <li
                v-for="reason in alert.reasons"
                :key="reason"
                class="flex items-start gap-2 text-[0.875rem] leading-snug text-ink-muted"
              >
                <span class="mt-1.5 h-1 w-1 shrink-0 rounded-full bg-ink-subtle" aria-hidden="true" />
                <span>{{ reason }}</span>
              </li>
            </ul>
          </div>
        </section>

        <!-- Review history and actions -->
        <section class="fin-card min-w-0 p-4 sm:p-5" aria-labelledby="fraud-review-heading">
          <h2 id="fraud-review-heading" class="text-headline text-ink">Review history</h2>

          <dl class="mt-2 divide-y divide-border border-t border-border">
            <DetailRow
              label="Reviewed by"
              :value="alert.reviewedBy ?? undefined"
              :hint="alert.reviewedBy ? undefined : 'Not yet reviewed.'"
            />
            <DetailRow
              label="Reviewed at"
              :value="alert.reviewedAt ? formatDateTime(alert.reviewedAt) : undefined"
              :hint="alert.reviewedAt ? formatRelative(alert.reviewedAt) : 'Not yet reviewed.'"
            />
            <DetailRow
              label="Review note"
              :value="alert.reviewNote ?? undefined"
              :hint="alert.reviewNote ? undefined : 'No note was recorded with this decision.'"
            />
            <DetailRow
              label="Average platform score"
              :value="stats ? `${stats.averageRiskScore} / 100` : undefined"
              :hint="stats ? `Across ${stats.totalAssessedToday.toLocaleString('en-US')} assessments today.` : 'Fraud statistics unavailable.'"
            />
          </dl>

          <h3 class="mt-5 text-headline text-ink">Decide</h3>
          <p class="mt-0.5 text-caption text-ink-muted">
            Every decision is written to the audit trail with your note.
          </p>

          <div class="mt-4 grid gap-2 sm:grid-cols-2">
            <BaseButton
              variant="secondary"
              :disabled="acting || alert.status === 'SAFE' || alert.status === 'CONFIRMED'"
              @click="ask('review')"
            >
              Start review
            </BaseButton>

            <BaseButton
              variant="secondary"
              :disabled="acting || alert.status === 'SAFE' || alert.status === 'CONFIRMED'"
              @click="ask('safe')"
            >
              <template #icon>
                <CheckCircle2 :size="15" aria-hidden="true" />
              </template>
              Mark Safe
            </BaseButton>

            <BaseButton
              :disabled="acting || alert.status === 'CONFIRMED'"
              @click="ask('confirm')"
            >
              <template #icon>
                <ShieldAlert :size="15" aria-hidden="true" />
              </template>
              Confirm Fraud
            </BaseButton>

            <BaseButton
              variant="danger"
              :disabled="acting || alert.status === 'CONFIRMED'"
              @click="ask('block')"
            >
              <template #icon>
                <Ban :size="15" aria-hidden="true" />
              </template>
              Block Account
            </BaseButton>
          </div>

          <p v-if="alert.status === 'SAFE' || alert.status === 'CONFIRMED'" class="mt-3 text-caption text-ink-subtle">
            This alert is already closed as {{ alert.status === 'SAFE' ? 'safe' : 'confirmed fraud' }} —
            Finova refuses further decisions on it.
          </p>
        </section>
      </div>

      <BaseButton variant="ghost" size="sm" :loading="loading" @click="load">
        <template #icon>
          <RefreshCw :size="15" aria-hidden="true" />
        </template>
        Refresh this alert
      </BaseButton>
    </template>

    <ConfirmDialog
      :open="confirmOpen"
      :title="confirmTitle"
      :message="confirmMessage"
      :detail="confirmDetail"
      :confirm-label="confirmLabel"
      :tone="action === 'safe' || action === 'review' ? 'warning' : 'danger'"
      :require-typing="action === 'block'"
      :typing-phrase="TYPING_PHRASE"
      :loading="acting"
      @confirm="runAction"
      @cancel="confirmOpen = false"
    >
      <BaseInput
        v-model="note"
        label="Note for the audit trail"
        :placeholder="
          action === 'safe'
            ? 'Why this transfer looked legitimate'
            : action === 'confirm'
              ? 'What made this fraud, and what recovery you intend to pursue'
              : action === 'block'
                ? 'Why this account is being blocked'
                : 'What you are checking'
        "
        :maxlength="400"
        hint="Stored on the alert and in the audit log alongside your name."
      />
    </ConfirmDialog>
  </div>
</template>