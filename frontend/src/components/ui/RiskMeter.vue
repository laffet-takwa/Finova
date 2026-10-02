<script setup lang="ts">
import { computed } from 'vue'
import type { RiskLevel } from '@/types'
import { RISK_LABELS } from '@/utils/format'

const props = withDefaults(
  defineProps<{
    score: number | null | undefined
    level?: RiskLevel | string | null
    size?: 'sm' | 'md'
    /** Accessible name for the meter, read by screen readers. */
    label?: string
  }>(),
  { level: null, size: 'md', label: 'Risk score' },
)

const BAND_FILL: Record<string, string> = {
  HIGH: 'bg-danger',
  MEDIUM: 'bg-warning',
  LOW: 'bg-success',
}

/** A score with no band is banded from its own value so the meter is never colourless. */
const band = computed<string>(() => {
  if (props.level === 'HIGH' || props.level === 'MEDIUM' || props.level === 'LOW') return props.level
  const score = props.score ?? 0
  return score >= 70 ? 'HIGH' : score >= 40 ? 'MEDIUM' : 'LOW'
})

const bandLabel = computed(() => RISK_LABELS[band.value as RiskLevel])

const percent = computed(() => {
  if (props.score === null || props.score === undefined || Number.isNaN(props.score)) return 0
  return Math.min(100, Math.max(0, props.score))
})

const fillClass = computed(() => BAND_FILL[band.value] ?? BAND_FILL.LOW)

const scoreText = computed(() =>
  props.score === null || props.score === undefined || Number.isNaN(props.score)
    ? '—'
    : String(Math.round(props.score)),
)

const accessibleText = computed(() =>
  props.score === null || props.score === undefined
    ? `${props.label}: not scored`
    : `${props.label}: ${scoreText.value} out of 100, ${bandLabel.value}`,
)
</script>

<template>
  <div class="min-w-0">
    <div class="flex items-baseline justify-between gap-2">
      <span class="text-caption font-semibold uppercase tracking-wider text-ink-subtle">
        {{ bandLabel }}
      </span>
      <!-- The literal score is always rendered: the bar is never the only signal. -->
      <span class="text-caption font-semibold tabular-nums text-ink">
        {{ scoreText }} / 100
      </span>
    </div>
    <div
      class="mt-1.5 w-full overflow-hidden rounded-full bg-border/70"
      :class="size === 'sm' ? 'h-1.5' : 'h-2'"
      role="img"
      :aria-label="accessibleText"
    >
      <div
        class="h-full rounded-full transition-[width] duration-300"
        :class="fillClass"
        :style="{ width: `${percent}%` }"
      />
    </div>
  </div>
</template>