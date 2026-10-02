<script setup lang="ts">
import { computed } from 'vue'
import { Check, X } from 'lucide-vue-next'
import { evaluatePassword } from '@/utils/format'

const props = withDefaults(
  defineProps<{
    password: string
    showRequirements?: boolean
    /** Visible label above the meter, e.g. "New password". */
    label?: string
  }>(),
  { showRequirements: true, label: 'Password strength' },
)

const strength = computed(() => evaluatePassword(props.password))

const SEGMENT_TONE: Record<0 | 1 | 2 | 3, string> = {
  0: 'bg-danger',
  1: 'bg-warning',
  2: 'bg-success',
  3: 'bg-success',
}

const LABEL_TONE: Record<0 | 1 | 2 | 3, string> = {
  0: 'text-danger',
  1: 'text-warning-dark',
  2: 'text-success-dark',
  3: 'text-success-dark',
}

const filled = computed(() => Math.max(strength.value.score, props.password ? 1 : 0))
</script>

<template>
  <div class="mt-2">
    <div class="flex items-center justify-between gap-3">
      <p class="text-caption font-semibold uppercase tracking-wider text-ink-subtle">{{ label }}</p>
      <p class="text-caption font-semibold" :class="LABEL_TONE[strength.score]" aria-live="polite">
        {{ password ? strength.label : 'Not set' }}
      </p>
    </div>

    <div class="mt-1.5 flex gap-1.5" aria-hidden="true">
      <span
        v-for="segment in [0, 1, 2, 3]"
        :key="segment"
        class="h-1.5 flex-1 rounded-full transition-colors"
        :class="segment < filled ? SEGMENT_TONE[strength.score] : 'bg-border'"
      />
    </div>

    <ul v-if="showRequirements" class="mt-2.5 grid gap-1.5 sm:grid-cols-2">
      <li
        v-for="requirement in strength.requirements"
        :key="requirement.label"
        class="flex items-center gap-1.5 text-caption"
        :class="requirement.met ? 'text-success-dark' : 'text-ink-subtle'"
      >
        <Check v-if="requirement.met" :size="13" aria-hidden="true" />
        <X v-else :size="13" aria-hidden="true" />
        <span>
          {{ requirement.label }}
          <span class="sr-only">— {{ requirement.met ? 'met' : 'not met yet' }}</span>
        </span>
      </li>
    </ul>
  </div>
</template>
