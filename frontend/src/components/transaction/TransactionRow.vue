<script setup lang="ts">
import { computed } from 'vue'
import { ArrowDownLeft, ArrowUpRight, Landmark, TrendingUp, Wallet } from 'lucide-vue-next'
import type { Transaction, TransactionStatus } from '@/types'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import { formatAmount, formatRelative } from '@/utils/format'

const props = withDefaults(
  defineProps<{
    transaction: Transaction
    perspective?: 'sender' | 'receiver' | 'neutral'
    showStatus?: boolean
    compact?: boolean
  }>(),
  { perspective: 'neutral', showStatus: true, compact: false },
)

defineEmits<{ click: [] }>()

const isCredit = computed(() => props.perspective === 'receiver')
const isDebit = computed(() => props.perspective === 'sender')

const signedAmount = computed(() => {
  if (isCredit.value) return `+${formatAmount(props.transaction.amount)}`
  if (isDebit.value) return `−${formatAmount(props.transaction.amount)}`
  return formatAmount(props.transaction.amount)
})

const amountTone = computed(() => {
  if (props.transaction.status === 'FAILED' || props.transaction.status === 'REJECTED') {
    return 'text-ink-subtle line-through decoration-1'
  }
  if (isCredit.value) return 'text-success'
  if (isDebit.value) return 'text-ink'
  return 'text-ink'
})

const counterpartLabel = computed(() => {
  if (isCredit.value) return `From ${props.transaction.senderDisplay}`
  if (isDebit.value) return `To ${props.transaction.receiverDisplay}`
  return props.transaction.receiverDisplay
})

const merchantIcon = computed(() => {
  if (isCredit.value) return ArrowDownLeft
  if (props.transaction.type === 'TRANSFER') return ArrowUpRight
  return Wallet
})

const iconTone = computed(() => {
  if (isCredit.value) return 'bg-success-light text-success'
  if (props.transaction.type === 'TRANSFER') return 'bg-primary-soft text-primary'
  return 'bg-surface-sunken text-ink-muted'
})

const statusTone = computed<TransactionStatus>(() => props.transaction.status)

const displayTitle = computed(() => {
  const description = props.transaction.description?.trim()
  if (description) return description
  if (isCredit.value) return 'Incoming transfer'
  if (props.transaction.type === 'DEPOSIT') return 'Deposit'
  if (props.transaction.type === 'WITHDRAWAL') return 'Withdrawal'
  return 'Transfer'
})
</script>

<template>
  <button
    type="button"
    class="flex w-full items-center gap-3 rounded-md px-2 py-2.5 text-left transition-colors hover:bg-surface-sunken"
    @click="$emit('click')"
  >
    <span
      class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full"
      :class="iconTone"
      aria-hidden="true"
    >
      <component :is="merchantIcon" :size="17" />
    </span>

    <span class="min-w-0 flex-1">
      <span class="flex items-center gap-2">
        <span class="truncate text-[0.9375rem] font-medium text-ink">{{ displayTitle }}</span>
        <StatusBadge v-if="showStatus && !compact" :status="statusTone" size="sm" />
      </span>
      <span class="mt-0.5 flex items-center gap-1.5 text-caption text-ink-subtle">
        <Landmark v-if="counterpartLabel" :size="11" aria-hidden="true" />
        <span class="truncate">{{ counterpartLabel }}</span>
        <span aria-hidden="true">·</span>
        <time :datetime="transaction.createdAt">{{ formatRelative(transaction.createdAt) }}</time>
      </span>
    </span>

    <span class="shrink-0 text-right">
      <span class="fin-amount block text-[0.9375rem]" :class="amountTone">
        {{ signedAmount }}
      </span>
      <span class="mt-0.5 block text-caption text-ink-subtle">{{ transaction.currency }}</span>
    </span>
  </button>
</template>