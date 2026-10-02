<script setup lang="ts">
import { computed } from 'vue'
import type { Transaction } from '@/types'
import RiskBadge from '@/components/ui/RiskBadge.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import Skeleton from '@/components/ui/Skeleton.vue'
import Pagination from '@/components/ui/Pagination.vue'
import { formatAmount, formatDateTime } from '@/utils/format'
import { Receipt } from 'lucide-vue-next'

const props = withDefaults(
  defineProps<{
    transactions: Transaction[]
    loading?: boolean
    page?: number
    totalPages?: number
    totalElements?: number
    pageSize?: number
    showRisk?: boolean
    /**
     * Adds a "User" column: the account the movement was made from, because the
     * transaction contract carries no owner `userId`. Used by the admin console,
     * where an operator needs to see whose money moved.
     */
    showSender?: boolean
    emptyTitle?: string
    emptyDescription?: string
    /** Label for the empty-state action button; hosts with nothing to send override it. */
    emptyActionLabel?: string
  }>(),
  {
    loading: false,
    page: 0,
    totalPages: 0,
    totalElements: 0,
    pageSize: 20,
    showRisk: false,
    showSender: false,
    emptyTitle: 'No transactions yet',
    emptyDescription:
      'Your transactions will appear here once you make your first transfer.',
    emptyActionLabel: 'Send money',
  },
)

const emit = defineEmits<{
  select: [transaction: Transaction]
  pageChange: [page: number]
  sizeChange: [size: number]
  emptyAction: []
}>()

const isEmpty = computed(() => !props.loading && props.transactions.length === 0)

function direction(transaction: Transaction): 'in' | 'out' | 'flat' {
  return transaction.type === 'DEPOSIT' ? 'in' : transaction.type === 'WITHDRAWAL' ? 'out' : 'flat'
}

function signedAmount(transaction: Transaction): string {
  const amount = formatAmount(transaction.amount)
  if (direction(transaction) === 'in') return `+${amount}`
  if (direction(transaction) === 'out') return `−${amount}`
  return amount
}
</script>

<template>
  <div class="fin-card overflow-hidden">
    <!-- Desktop: financial table -->
    <div v-if="!loading && !isEmpty" class="hidden lg:block">
      <div class="fin-scroll-thin overflow-x-auto">
        <table class="w-full min-w-[52rem] border-collapse">
          <caption class="sr-only">Transaction history</caption>
          <thead>
            <tr>
              <th scope="col" class="fin-table-header">Reference</th>
              <th v-if="showSender" scope="col" class="fin-table-header">User</th>
              <th scope="col" class="fin-table-header">Date</th>
              <th scope="col" class="fin-table-header">Description</th>
              <th scope="col" class="fin-table-header">Type</th>
              <th scope="col" class="fin-table-header text-right">Amount</th>
              <th v-if="showRisk" scope="col" class="fin-table-header">Risk</th>
              <th scope="col" class="fin-table-header">Status</th>
              <th v-if="$slots.rowActions" scope="col" class="fin-table-header text-right">
                <span class="sr-only">Row actions</span>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="transaction in transactions"
              :key="transaction.id"
              class="cursor-pointer transition-colors hover:bg-surface-sunken"
              tabindex="0"
              @click="emit('select', transaction)"
              @keydown.enter="emit('select', transaction)"
              @keydown.space.prevent="emit('select', transaction)"
            >
              <td class="fin-table-cell">
                <span class="font-mono text-[0.8125rem] font-medium text-ink">
                  {{ transaction.reference }}
                </span>
              </td>
              <td v-if="showSender" class="fin-table-cell">
                <span class="block max-w-[12rem] truncate text-[0.9375rem] text-ink">
                  {{ transaction.senderDisplay || '—' }}
                </span>
                <span class="mt-0.5 block font-mono text-caption text-ink-subtle">
                  {{ transaction.senderAccountNumber }}
                </span>
              </td>
              <td class="fin-table-cell whitespace-nowrap text-[0.875rem] text-ink-muted">
                {{ formatDateTime(transaction.createdAt) }}
              </td>
              <td class="fin-table-cell">
                <span class="block max-w-[18rem] truncate text-[0.9375rem] text-ink">
                  {{ transaction.description || '—' }}
                </span>
                <span class="mt-0.5 block text-caption text-ink-subtle">
                  {{ transaction.senderDisplay }} → {{ transaction.receiverDisplay }}
                </span>
              </td>
              <td class="fin-table-cell">
                <span class="text-[0.8125rem] font-medium uppercase tracking-wide text-ink-muted">
                  {{ transaction.type }}
                </span>
              </td>
              <td class="fin-table-cell text-right">
                <span
                  class="fin-amount text-[0.9375rem]"
                  :class="transaction.status === 'FAILED' || transaction.status === 'REJECTED' ? 'text-ink-subtle' : ''"
                >
                  {{ signedAmount(transaction) }}
                </span>
                <span class="mt-0.5 block text-caption text-ink-subtle">{{ transaction.currency }}</span>
              </td>
              <td v-if="showRisk" class="fin-table-cell">
                <RiskBadge
                  v-if="transaction.riskLevel"
                  :level="transaction.riskLevel"
                  :score="transaction.riskScore"
                  size="sm"
                />
                <span v-else class="text-caption text-ink-subtle">—</span>
              </td>
              <td class="fin-table-cell">
                <StatusBadge :status="transaction.status" />
              </td>
              <td
                v-if="$slots.rowActions"
                class="fin-table-cell whitespace-nowrap text-right"
                @click.stop
                @keydown.stop
              >
                <slot name="rowActions" :transaction="transaction" />
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Mobile / tablet: cards -->
    <div v-if="!loading && !isEmpty" class="divide-y divide-border lg:hidden">
      <div
        v-for="transaction in transactions"
        :key="transaction.id"
        class="transition-colors hover:bg-surface-sunken"
      >
        <button
          type="button"
          class="flex w-full flex-col gap-2 p-4 text-left"
          @click="emit('select', transaction)"
        >
          <div class="flex items-start justify-between gap-3">
            <div class="min-w-0">
              <p class="truncate text-[0.9375rem] font-medium text-ink">
                {{ transaction.description || transaction.type }}
              </p>
              <p class="mt-0.5 font-mono text-caption text-ink-subtle">{{ transaction.reference }}</p>
            </div>
            <div class="shrink-0 text-right">
              <p class="fin-amount text-[0.9375rem]">{{ signedAmount(transaction) }}</p>
              <p class="text-caption text-ink-subtle">{{ transaction.currency }}</p>
            </div>
          </div>

          <div class="flex flex-wrap items-center gap-2">
            <StatusBadge :status="transaction.status" size="sm" />
            <RiskBadge
              v-if="showRisk && transaction.riskLevel"
              :level="transaction.riskLevel"
              :score="transaction.riskScore"
              size="sm"
            />
            <time :datetime="transaction.createdAt" class="ml-auto text-caption text-ink-subtle">
              {{ formatDateTime(transaction.createdAt) }}
            </time>
          </div>

          <p v-if="showSender" class="text-caption text-ink-subtle">
            From {{ transaction.senderDisplay || '—' }}
            <span class="font-mono">{{ transaction.senderAccountNumber }}</span>
          </p>

          <p class="text-caption text-ink-subtle">
            {{ transaction.senderDisplay }} → {{ transaction.receiverDisplay }}
          </p>
        </button>

        <div
          v-if="$slots.rowActions"
          class="flex flex-wrap items-center justify-end gap-2 border-t border-border px-4 pb-4 pt-3"
        >
          <slot name="rowActions" :transaction="transaction" />
        </div>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="divide-y divide-border" aria-busy="true" aria-live="polite">
      <div v-for="row in 6" :key="row" class="flex items-center gap-3 p-4">
        <div class="fin-skeleton h-9 w-9 rounded-full" />
        <div class="flex-1 space-y-2">
          <Skeleton width="55%" />
          <Skeleton width="32%" />
        </div>
        <Skeleton width="22%" />
      </div>
      <span class="sr-only">Loading transactions</span>
    </div>

    <!-- Empty -->
    <EmptyState
      v-if="isEmpty"
      :title="emptyTitle"
      :description="emptyDescription"
      :icon="Receipt"
      compact
    >
      <button type="button" class="fin-btn-primary" @click="emit('emptyAction')">
        {{ emptyActionLabel }}
      </button>
    </EmptyState>

    <!-- Pagination -->
    <div
      v-if="!loading && !isEmpty && totalElements > 0"
      class="border-t border-border px-4 py-3.5"
    >
      <Pagination
        :page="page"
        :total-pages="totalPages"
        :total-elements="totalElements"
        :page-size="pageSize"
        label="transactions"
        @change="emit('pageChange', $event)"
        @size-change="emit('sizeChange', $event)"
      />
    </div>
  </div>
</template>