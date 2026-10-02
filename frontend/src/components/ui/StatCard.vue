<script setup lang="ts">
import { computed } from 'vue'
import { ArrowDownRight, ArrowUpRight, Minus } from 'lucide-vue-next'

const props = withDefaults(
  defineProps<{
    label: string
    value: string
    delta?: number | null
    deltaLabel?: string
    hint?: string
    tone?: 'neutral' | 'success' | 'warning' | 'danger' | 'primary'
    loading?: boolean
  }>(),
  { delta: null, deltaLabel: 'vs last month', hint: undefined, tone: 'neutral', loading: false },
)

const toneClasses: Record<string, string> = {
  neutral: 'text-ink',
  success: 'text-success',
  warning: 'text-warning-dark',
  danger: 'text-danger',
  primary: 'text-primary',
}

const deltaIcon = computed(() => {
  if (props.delta === null || props.delta === undefined || props.delta === 0) return Minus
  return props.delta > 0 ? ArrowUpRight : ArrowDownRight
})

const deltaTone = computed(() => {
  if (props.delta === null || props.delta === undefined || props.delta === 0) return 'text-ink-subtle'
  return props.delta > 0 ? 'text-success' : 'text-danger'
})
</script>

<template>
  <div class="fin-card flex flex-col justify-between p-4 sm:p-5">
    <div class="flex items-start justify-between gap-3">
      <p class="text-caption font-semibold uppercase tracking-wider text-ink-subtle">{{ label }}</p>
      <div
        v-if="$slots.icon"
        class="flex h-8 w-8 shrink-0 items-center justify-center rounded-md"
        :class="{
          'bg-primary-soft text-primary': tone === 'primary' || tone === 'neutral',
          'bg-success-light text-success': tone === 'success',
          'bg-warning-light text-warning-dark': tone === 'warning',
          'bg-danger-light text-danger': tone === 'danger',
        }"
        aria-hidden="true"
      >
        <slot name="icon" />
      </div>
    </div>

    <div class="mt-3">
      <p v-if="loading" class="fin-skeleton h-8 w-32" />
      <p
        v-else
        class="fin-amount text-[1.75rem] leading-tight"
        :class="toneClasses[tone]"
      >
        {{ value }}
      </p>

      <div v-if="delta !== null && delta !== undefined || hint" class="mt-1.5 flex items-center gap-2">
        <span
          v-if="delta !== null && delta !== undefined"
          class="inline-flex items-center gap-0.5 text-caption font-semibold"
          :class="deltaTone"
        >
          <component :is="deltaIcon" :size="13" aria-hidden="true" />
          {{ delta > 0 ? '+' : '' }}{{ delta.toFixed(1) }}%
        </span>
        <span v-if="deltaLabel && delta !== null && delta !== undefined" class="text-caption text-ink-subtle">
          {{ deltaLabel }}
        </span>
        <span v-else-if="hint" class="text-caption text-ink-subtle">{{ hint }}</span>
      </div>
    </div>
  </div>
</template>