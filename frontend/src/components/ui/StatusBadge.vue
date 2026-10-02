<script setup lang="ts">
import { computed } from 'vue'
import type { AccountStatus, FraudStatus, TransactionStatus } from '@/types'

type Status = TransactionStatus | AccountStatus | FraudStatus | string

const props = withDefaults(defineProps<{ status: Status; size?: 'sm' | 'md' }>(), { size: 'md' })

const styles: Record<string, { chip: string; dot: string; label: string }> = {
  COMPLETED: { chip: 'bg-success-light text-success-dark', dot: 'bg-success', label: 'Completed' },
  ACTIVE: { chip: 'bg-success-light text-success-dark', dot: 'bg-success', label: 'Active' },
  SAFE: { chip: 'bg-success-light text-success-dark', dot: 'bg-success', label: 'Safe' },
  PENDING: { chip: 'bg-primary-soft text-primary', dot: 'bg-primary', label: 'Pending' },
  PROCESSING: { chip: 'bg-primary-soft text-primary', dot: 'bg-accent', label: 'Processing' },
  UNDER_REVIEW: { chip: 'bg-accent-light text-accent-dark', dot: 'bg-accent', label: 'Under review' },
  FLAGGED: { chip: 'bg-warning-light text-warning-dark', dot: 'bg-warning', label: 'Flagged' },
  OPEN: { chip: 'bg-warning-light text-warning-dark', dot: 'bg-warning', label: 'Open' },
  BLOCKED: { chip: 'bg-danger-light text-danger-dark', dot: 'bg-danger', label: 'Blocked' },
  CONFIRMED: { chip: 'bg-danger-light text-danger-dark', dot: 'bg-danger', label: 'Confirmed' },
  FAILED: { chip: 'bg-danger-light text-danger-dark', dot: 'bg-danger', label: 'Failed' },
  REJECTED: { chip: 'bg-danger-light text-danger-dark', dot: 'bg-danger', label: 'Rejected' },
  CLOSED: { chip: 'bg-border/60 text-ink-muted', dot: 'bg-ink-subtle', label: 'Closed' },
}

const fallback = { chip: 'bg-border/60 text-ink-muted', dot: 'bg-ink-subtle', label: '' }

const style = computed(() => styles[props.status] ?? { ...fallback, label: props.status })
</script>

<template>
  <span
    class="fin-chip"
    :class="[style.chip, size === 'sm' ? 'h-6 px-2 text-[0.6875rem]' : '']"
    :title="style.label"
  >
    <span class="h-1.5 w-1.5 rounded-full" :class="style.dot" aria-hidden="true" />
    <span>{{ style.label || status }}</span>
  </span>
</template>