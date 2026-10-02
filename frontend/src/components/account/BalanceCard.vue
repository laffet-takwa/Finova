<script setup lang="ts">
import { computed } from 'vue'
import { ArrowDownLeft, ArrowUpRight, Eye, EyeOff, TrendingUp } from 'lucide-vue-next'
import type { Account, Currency } from '@/types'
import BaseButton from '@/components/ui/BaseButton.vue'
import { formatAmount } from '@/utils/format'

const props = withDefaults(
  defineProps<{
    total: number
    currency: Currency | string
    monthlyChangePercent?: number | null
    accountCount?: number
    loading?: boolean
    hidden?: boolean
  }>(),
  { monthlyChangePercent: null, accountCount: 0, loading: false, hidden: false },
)

const emit = defineEmits<{
  send: []
  viewAccounts: []
  toggleVisibility: []
}>()

const changeText = computed(() => {
  if (props.monthlyChangePercent === null || props.monthlyChangePercent === undefined) return null
  const value = props.monthlyChangePercent
  return `${value >= 0 ? '+' : ''}${value.toFixed(1)}% this month`
})

const changeTone = computed(() => {
  if (props.monthlyChangePercent === null || props.monthlyChangePercent === undefined) return ''
  return props.monthlyChangePercent >= 0 ? 'text-accent-light' : 'text-danger-light'
})

const masked = computed(() => '•••••••')
</script>

<template>
  <section
    class="relative overflow-hidden rounded-xl bg-primary-dark p-6 text-white shadow-raised sm:p-7"
    aria-labelledby="total-balance-heading"
  >
    <div
      class="pointer-events-none absolute -right-16 -top-20 h-56 w-56 rounded-full bg-primary/60 blur-2xl"
      aria-hidden="true"
    />
    <div
      class="pointer-events-none absolute -bottom-24 -left-10 h-48 w-48 rounded-full bg-accent/15 blur-2xl"
      aria-hidden="true"
    />

    <div class="relative">
      <header class="flex items-start justify-between gap-4">
        <div>
          <p class="text-caption font-semibold uppercase tracking-[0.16em] text-white/60">
            Total balance
          </p>
          <button
            v-if="$attrs.onToggleVisibility !== undefined || true"
            type="button"
            class="mt-1 flex items-center gap-1.5 text-[0.6875rem] font-medium text-white/50 transition-colors hover:text-white/80"
            :aria-label="hidden ? 'Show balances' : 'Hide balances'"
            @click="emit('toggleVisibility')"
          >
            <EyeOff v-if="!hidden" :size="12" aria-hidden="true" />
            <Eye v-else :size="12" aria-hidden="true" />
            {{ hidden ? 'Show balances' : 'Hide balances' }}
          </button>
        </div>

        <span
          v-if="accountCount > 0"
          class="rounded-full bg-white/10 px-3 py-1 text-caption font-medium text-white/80"
        >
          {{ accountCount }} {{ accountCount === 1 ? 'account' : 'accounts' }}
        </span>
      </header>

      <h2 id="total-balance-heading" class="mt-3 flex flex-wrap items-baseline gap-x-3 gap-y-1">
        <span v-if="loading" class="fin-skeleton h-11 w-56 bg-white/15" />
        <span
          v-else
          class="font-display text-display-lg font-bold tracking-tight text-white tabular-nums"
        >
          {{ hidden ? masked : formatAmount(total) }}
        </span>
        <span
          v-if="!loading"
          class="font-display text-lg font-semibold text-white/60 tabular-nums"
        >
          {{ currency }}
        </span>
      </h2>

      <p
        v-if="changeText && !hidden"
        class="mt-2 inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-caption font-semibold"
        :class="changeTone"
      >
        <TrendingUp v-if="(monthlyChangePercent ?? 0) >= 0" :size="13" aria-hidden="true" />
        {{ changeText }}
      </p>

      <div class="mt-6 flex flex-wrap gap-2.5">
        <BaseButton variant="accent" @click="emit('send')">
          <template #icon>
            <ArrowUpRight :size="16" aria-hidden="true" />
          </template>
          Send money
        </BaseButton>

        <BaseButton
          variant="secondary"
          class="!border-white/25 !bg-white/10 !text-white hover:!bg-white/15"
          @click="emit('viewAccounts')"
        >
          <template #icon>
            <ArrowDownLeft :size="16" aria-hidden="true" />
          </template>
          View accounts
        </BaseButton>
      </div>
    </div>
  </section>
</template>