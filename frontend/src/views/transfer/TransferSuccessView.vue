<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, Check, Clock, TriangleAlert, X } from 'lucide-vue-next'
import PageHeader from '@/components/ui/PageHeader.vue'
import StepProgress, { type StepProgressItem } from '@/components/ui/StepProgress.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import DetailRow from '@/components/ui/DetailRow.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import RiskBadge from '@/components/ui/RiskBadge.vue'
import Skeleton from '@/components/ui/Skeleton.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import { useTransactionStore } from '@/stores/transactionStore'
import { AppError } from '@/utils/errors'
import { formatDateTime, formatMoney } from '@/utils/format'
import type { Transaction } from '@/types'

const STEPS: StepProgressItem[] = [
  { label: 'Recipient' },
  { label: 'Amount' },
  { label: 'Review' },
  { label: 'Complete' },
]

type Outcome = 'completed' | 'held' | 'submitted' | 'failed'

const route = useRoute()
const router = useRouter()
const transactionStore = useTransactionStore()

const loading = ref(true)
const loadError = ref<string | null>(null)
const loadOffline = ref(false)
const transaction = ref<Transaction | null>(null)

const id = computed(() => (typeof route.params.id === 'string' ? route.params.id : ''))

const outcome = computed<Outcome>(() => {
  const status = transaction.value?.status
  if (!status) return 'submitted'
  if (status === 'COMPLETED') return 'completed'
  if (status === 'FLAGGED' || status === 'REJECTED') return 'held'
  if (status === 'FAILED') return 'failed'
  return 'submitted'
})

const COPY: Record<Outcome, { title: string; lead: string; detail: string; icon: typeof Check; surface: string; ring: string; tone: string }> = {
  completed: {
    title: 'Transfer completed',
    lead: 'sent successfully.',
    detail: 'The money has left your account and been credited to the recipient.',
    icon: Check,
    surface: 'bg-success-light',
    ring: 'text-success',
    tone: 'text-success-dark',
  },
  held: {
    title: 'Transfer held for review',
    lead: 'is being reviewed by our security team.',
    detail:
      'No money has moved yet. Finova releases or cancels held transfers once the review is complete, and you will be notified either way.',
    icon: TriangleAlert,
    surface: 'bg-warning-light',
    ring: 'text-warning-dark',
    tone: 'text-warning-dark',
  },
  submitted: {
    title: 'Transfer submitted',
    lead: 'is being processed.',
    detail:
      'The payment is in progress. It will complete within a few minutes unless our risk engine holds it for review.',
    icon: Clock,
    surface: 'bg-primary-soft',
    ring: 'text-primary',
    tone: 'text-primary',
  },
  failed: {
    title: 'Transfer failed',
    lead: 'was not completed.',
    detail: 'Nothing was debited from your account.',
    icon: X,
    surface: 'bg-danger-light',
    ring: 'text-danger',
    tone: 'text-danger-dark',
  },
}

const copy = computed(() => COPY[outcome.value])

async function load(force = false): Promise<void> {
  if (!id.value) {
    loading.value = false
    loadError.value = 'This link does not identify a transfer.'
    return
  }
  loading.value = true
  loadError.value = null
  try {
    transaction.value = await transactionStore.fetchOne(id.value, force)
  } catch (cause) {
    transaction.value = null
    loadError.value = cause instanceof Error ? cause.message : 'We could not load this transfer.'
    loadOffline.value = cause instanceof AppError ? cause.isNetworkError : false
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  // The flow is over: drop the draft so a later visit starts clean.
  transactionStore.clearTransferDraft()
  void load(true)
})
</script>

<template>
  <div class="space-y-5 pb-4">
    <PageHeader eyebrow="Step 4 of 4" title="Transfer status" />

    <div v-if="loading" class="fin-card space-y-4 p-5" aria-busy="true">
      <Skeleton variant="block" :rows="3" />
      <span class="sr-only">Loading your transfer</span>
    </div>

    <ErrorState
      v-else-if="loadError"
      class="fin-card"
      title="We could not load this transfer"
      :description="loadError"
      :offline="loadOffline"
      retry-label="Try again"
      @retry="load(true)"
    >
      <BaseButton variant="ghost" size="sm" @click="router.push({ name: 'transactions' })">
        Back to transactions
      </BaseButton>
    </ErrorState>

    <template v-else-if="transaction">
      <section class="fin-card p-4 sm:p-5">
        <StepProgress :steps="STEPS" :current="4" aria-label="Transfer progress" />
      </section>

      <section class="fin-card overflow-hidden">
        <div class="px-5 py-8 text-center sm:px-8 sm:py-10">
          <span
            class="mx-auto flex h-16 w-16 items-center justify-center rounded-full"
            :class="copy.surface"
          >
            <!-- One short stroke draw on the happy path; static for everything else. -->
            <svg
              v-if="outcome === 'completed'"
              viewBox="0 0 48 48"
              class="h-9 w-9 animate-scale-in motion-reduce:animate-none"
              :class="copy.ring"
              aria-hidden="true"
            >
              <path
                d="M4 18 L14 28 L38 4"
                fill="none"
                stroke="currentColor"
                stroke-width="4.5"
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-dasharray="48"
                class="animate-check-stroke motion-reduce:animate-none"
              />
            </svg>
            <component
              v-else
              :is="copy.icon"
              :size="30"
              :stroke-width="2.4"
              class="animate-scale-in motion-reduce:animate-none"
              :class="copy.ring"
              aria-hidden="true"
            />
          </span>

          <h1 class="mt-5 text-title text-ink">{{ copy.title }}</h1>

          <p class="mt-2 text-[1.0625rem] text-ink-muted">
            <span class="fin-amount text-money text-ink">
              {{ formatMoney(transaction.amount, transaction.currency) }}
            </span>
            {{ copy.lead }}
          </p>

          <div class="mt-4 flex flex-wrap items-center justify-center gap-2">
            <StatusBadge :status="transaction.status" />
            <RiskBadge
              v-if="transaction.riskLevel"
              :level="transaction.riskLevel"
              :score="transaction.riskScore"
            />
          </div>

          <p class="mx-auto mt-4 max-w-md text-[0.9375rem] leading-relaxed text-ink-muted">
            {{ copy.detail }}
          </p>
        </div>

        <div class="border-t border-border bg-surface-sunken px-4 py-2 sm:px-6">
          <dl class="divide-y divide-border">
            <DetailRow label="Reference" :value="transaction.reference" mono />
            <DetailRow
              label="From"
              :value="`${transaction.senderDisplay} · ${transaction.senderAccountNumber}`"
            />
            <DetailRow
              label="To"
              :value="`${transaction.receiverDisplay} · ${transaction.receiverAccountNumber}`"
            />
            <DetailRow v-if="transaction.description" label="Description" :value="transaction.description" />
            <DetailRow
              v-if="transaction.status === 'FAILED' || transaction.status === 'FLAGGED' || transaction.status === 'REJECTED'"
              label="Reason"
              :value="transaction.failureReason ?? (transaction.status === 'FLAGGED' ? 'Held by the risk engine pending review.' : 'No reason was reported by Finova.')"
            />
            <DetailRow label="Created" :value="formatDateTime(transaction.createdAt)" />
            <DetailRow
              v-if="transaction.completedAt"
              label="Completed"
              :value="formatDateTime(transaction.completedAt)"
            />
          </dl>
        </div>

        <div class="flex flex-col gap-2 border-t border-border px-4 py-4 sm:flex-row sm:justify-center sm:px-6">
          <BaseButton variant="primary" :to="`/transactions/${id}`">
            View transaction
          </BaseButton>
          <BaseButton variant="secondary" to="/dashboard">Back to dashboard</BaseButton>
        </div>
      </section>

      <p v-if="outcome === 'held'" class="text-center text-caption text-ink-subtle">
        Held transfers are not cancelled automatically. Contact support with the reference above if you need help.
      </p>
    </template>
  </div>
</template>
