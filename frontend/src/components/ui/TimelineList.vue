<script setup lang="ts">
import { Check, Circle, X } from 'lucide-vue-next'
import type { TimelineState, TimelineStep } from '@/types'
import { formatDateTime } from '@/utils/format'

withDefaults(
  defineProps<{
    steps: TimelineStep[]
    ariaLabel?: string
    /** Optional formatter so the host view can honour the user's time-format preference. */
    formatTime?: (value: string) => string
  }>(),
  { ariaLabel: 'Transaction timeline', formatTime: undefined },
)

const STATE_META: Record<TimelineState, { label: string; chip: string; marker: string; icon: typeof Check }> = {
  DONE: { label: 'Completed', chip: 'bg-success-light text-success-dark', marker: 'bg-success text-white', icon: Check },
  CURRENT: { label: 'In progress', chip: 'bg-accent-light text-accent-dark', marker: 'bg-surface text-accent ring-2 ring-accent', icon: Circle },
  PENDING: { label: 'Not started', chip: 'bg-surface-sunken text-ink-subtle', marker: 'bg-surface text-ink-subtle ring-1 ring-border-strong', icon: Circle },
  FAILED: { label: 'Failed', chip: 'bg-danger-light text-danger-dark', marker: 'bg-danger text-white', icon: X },
}
</script>

<template>
  <ol :aria-label="ariaLabel" class="space-y-0">
    <li v-for="(step, index) in steps" :key="step.key" class="relative flex gap-3.5 pb-5 last:pb-0">
      <!-- Connector -->
      <span
        v-if="index < steps.length - 1"
        class="absolute left-[15px] top-8 h-[calc(100%-1.5rem)] w-px bg-border"
        aria-hidden="true"
      />

      <span
        class="relative z-10 flex h-8 w-8 shrink-0 items-center justify-center rounded-full"
        :class="STATE_META[step.state].marker"
        aria-hidden="true"
      >
        <component :is="STATE_META[step.state].icon" :size="15" />
      </span>

      <div class="min-w-0 flex-1 pt-0.5">
        <div class="flex flex-wrap items-center gap-x-2 gap-y-1">
          <p class="text-[0.9375rem] font-semibold text-ink">{{ step.label }}</p>
          <span class="fin-chip h-6" :class="STATE_META[step.state].chip">
            {{ STATE_META[step.state].label }}
          </span>
        </div>

        <p class="mt-1 text-[0.875rem] leading-relaxed text-ink-muted">{{ step.description }}</p>

        <!-- Only render a timestamp the backend actually provided. -->
        <p v-if="step.at" class="mt-1 text-caption tabular-nums text-ink-subtle">
          <time :datetime="step.at">
            {{ (formatTime ?? formatDateTime)(step.at) }}
          </time>
        </p>
      </div>
    </li>
  </ol>
</template>
