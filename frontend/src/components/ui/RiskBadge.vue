<script setup lang="ts">
import { computed } from 'vue'
import { ShieldAlert, ShieldCheck, ShieldQuestion } from 'lucide-vue-next'
import type { RiskLevel } from '@/types'

const props = withDefaults(
  defineProps<{ level: RiskLevel | string; score?: number | null; size?: 'sm' | 'md' }>(),
  { score: null, size: 'md' },
)

const config: Record<string, { chip: string; icon: typeof ShieldCheck; label: string }> = {
  HIGH: { chip: 'bg-danger-light text-danger-dark border-danger/20', icon: ShieldAlert, label: 'High risk' },
  MEDIUM: { chip: 'bg-warning-light text-warning-dark border-warning/25', icon: ShieldQuestion, label: 'Medium risk' },
  LOW: { chip: 'bg-success-light text-success-dark border-success/20', icon: ShieldCheck, label: 'Low risk' },
}

const style = computed(() => config[props.level] ?? config.LOW)
</script>

<template>
  <span
    class="inline-flex items-center gap-1.5 rounded border font-semibold"
    :class="[style.chip, props.size === 'sm' ? 'h-6 px-2 text-[0.6875rem]' : 'h-7 px-2.5 text-caption']"
  >
    <component :is="style.icon" :size="props.size === 'sm' ? 11 : 13" aria-hidden="true" />
    <span>{{ style.label }}</span>
    <span v-if="score !== null && score !== undefined" class="opacity-75 tabular-nums">{{ score }}/100</span>
  </span>
</template>