<script setup lang="ts">
import { computed } from 'vue'

/**
 * A score bar scaled against a caller-supplied maximum.
 *
 * `RiskMeter` is deliberately absolute (score out of 100) because that is what a
 * single alert means. In a ranked list that is useless: every flagged account sits
 * in the same 70–95 band, so all the bars look the same and the ranking is
 * unreadable. Passing the head of the list as `max` turns the same bar into a
 * ranking, while the literal score stays in the text beside it.
 */
const props = withDefaults(
  defineProps<{
    score: number
    /** Scale maximum. Defaults to the 0–100 risk scale. */
    max?: number
    level?: 'HIGH' | 'MEDIUM' | 'LOW'
    /** Describes the bar for assistive tech — the fill is never the only signal. */
    label: string
  }>(),
  { max: 100, level: undefined },
)

const fill = computed(() => {
  const scale = props.max > 0 ? props.max : 100
  const percent = (props.score / scale) * 100
  return `${Math.max(2, Math.min(100, percent))}%`
})

const band = computed<'HIGH' | 'MEDIUM' | 'LOW'>(() => {
  if (props.level) return props.level
  if (props.score >= 70) return 'HIGH'
  if (props.score >= 40) return 'MEDIUM'
  return 'LOW'
})

const fillClass = computed(() =>
  band.value === 'HIGH' ? 'bg-danger' : band.value === 'MEDIUM' ? 'bg-warning' : 'bg-success',
)
</script>

<template>
  <div
    class="h-1.5 w-full overflow-hidden rounded-full bg-surface-sunken"
    role="img"
    :aria-label="label"
  >
    <div
      class="h-full rounded-full transition-[width] duration-500 motion-reduce:transition-none"
      :class="fillClass"
      :style="{ width: fill }"
    />
  </div>
</template>