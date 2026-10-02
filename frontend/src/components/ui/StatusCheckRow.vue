<script setup lang="ts">
import { computed } from 'vue'
import { CircleSlash, CircleCheck, TriangleAlert } from 'lucide-vue-next'

const props = withDefaults(
  defineProps<{
    label: string
    description?: string
    /**
     * `ok` — the control is in place,
     * `attention` — the control does not exist yet and the user should know why,
     * `off` — the control is explicitly unavailable.
     */
    state?: 'ok' | 'attention' | 'off'
    /** Short status word shown next to the label, e.g. "Enabled" / "Not enabled". */
    value?: string
  }>(),
  { description: undefined, state: 'ok', value: undefined },
)

const CONFIG = {
  ok: {
    icon: CircleCheck,
    tone: 'text-success',
    surface: 'bg-success-light',
    stateLabel: 'In place',
  },
  attention: {
    icon: TriangleAlert,
    tone: 'text-warning-dark',
    surface: 'bg-warning-light',
    stateLabel: 'Not in place',
  },
  off: {
    icon: CircleSlash,
    tone: 'text-ink-subtle',
    surface: 'bg-surface-sunken',
    stateLabel: 'Unavailable',
  },
} as const

const config = computed(() => CONFIG[props.state])
</script>

<template>
  <li class="flex items-start gap-3.5 py-3.5">
    <span
      class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full"
      :class="[config.surface, config.tone]"
      aria-hidden="true"
    >
      <component :is="config.icon" :size="18" />
    </span>

    <div class="min-w-0 flex-1">
      <p class="flex flex-wrap items-center gap-x-2 gap-y-1">
        <span class="text-[0.9375rem] font-semibold text-ink">{{ label }}</span>
        <span v-if="value" class="fin-chip h-6" :class="[config.surface, config.tone]">
          {{ value }}
        </span>
        <span class="sr-only">{{ config.stateLabel }}</span>
      </p>
      <p v-if="description" class="mt-1 text-[0.875rem] leading-relaxed text-ink-muted">
        {{ description }}
      </p>
      <slot />
    </div>
  </li>
</template>
