<script setup lang="ts">
import { computed } from 'vue'
import { ArrowUpRight, Landmark, PiggyBank, Wallet } from 'lucide-vue-next'
import type { Account, AccountType } from '@/types'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import { formatAmount, formatDate } from '@/utils/format'

const props = defineProps<{ account: Account }>()

defineEmits<{ view: [account: Account]; transfer: [account: Account] }>()

const icon = computed(() => (props.account.accountType === 'SAVINGS' ? PiggyBank : Wallet))

const label = computed(() => {
  if (props.account.nickname) return props.account.nickname
  return props.account.accountType === 'SAVINGS' ? 'Savings Account' : 'Everyday Account'
})

const typeLabel = computed<AccountType>(() => props.account.accountType)
const isBlocked = computed(() => props.account.status !== 'ACTIVE')
</script>

<template>
  <article
    class="fin-card fin-card-hover flex flex-col p-5"
    :class="{ 'opacity-90': isBlocked }"
  >
    <header class="flex items-start justify-between gap-3">
      <div class="flex min-w-0 items-center gap-3">
        <span
          class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg"
          :class="account.accountType === 'SAVINGS' ? 'bg-success-light text-success' : 'bg-primary-soft text-primary'"
          aria-hidden="true"
        >
          <component :is="icon" :size="19" />
        </span>
        <div class="min-w-0">
          <h3 class="truncate text-headline text-ink">{{ label }}</h3>
          <p class="mt-0.5 flex items-center gap-1.5 font-mono text-caption text-ink-subtle">
            <Landmark :size="11" aria-hidden="true" />
            {{ account.maskedAccountNumber }}
          </p>
        </div>
      </div>
      <StatusBadge :status="account.status" size="sm" />
    </header>

    <div class="mt-5">
      <p class="text-caption font-semibold uppercase tracking-wider text-ink-subtle">
        Current balance
      </p>
      <p class="fin-amount mt-1 text-money text-ink">
        {{ formatAmount(account.balance) }}
        <span class="ml-1 text-base font-semibold text-ink-muted">{{ account.currency }}</span>
      </p>
      <p class="mt-1 text-caption text-ink-subtle">
        {{ typeLabel }} · opened {{ formatDate(account.createdAt) }}
      </p>
    </div>

    <footer class="mt-5 flex items-center gap-2 border-t border-border pt-4">
      <button type="button" class="fin-btn-secondary fin-btn-sm flex-1" @click="$emit('view', account)">
        View details
      </button>
      <button
        type="button"
        class="fin-btn-primary fin-btn-sm flex-1"
        :disabled="isBlocked"
        @click="$emit('transfer', account)"
      >
        <ArrowUpRight :size="14" aria-hidden="true" />
        Transfer
      </button>
    </footer>
  </article>
</template>