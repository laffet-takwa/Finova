<script setup lang="ts">
import { computed } from 'vue'
import { Check } from 'lucide-vue-next'

export interface StepProgressItem {
  label: string
  hint?: string
}

const props = withDefaults(
  defineProps<{
    steps: StepProgressItem[]
    /** 1-based index of the step the user is currently on. */
    current: number
    ariaLabel?: string
  }>(),
  { ariaLabel: 'Progress' },
)

const total = computed(() => Math.max(props.steps.length, 1))
const safeCurrent = computed(() => Math.min(Math.max(props.current, 1), total.value))
const percent = computed(() => Math.round((safeCurrent.value / total.value) * 100))

function stateFor(index: number): 'done' | 'current' | 'todo' {
  if (index + 1 < safeCurrent.value) return 'done'
  if (index + 1 === safeCurrent.value) return 'current'
  return 'todo'
}

const STATE_LABEL: Record<'done' | 'current' | 'todo', string> = {
  done: 'Completed',
  current: 'In progress',
  todo: 'Not started',
}
</script>

<template>
  <nav :aria-label="ariaLabel" class="w-full">
    <!-- Desktop / tablet: full stepper -->
    <ol class="hidden items-center sm:flex">
      <li v-for="(step, index) in steps" :key="step.label" class="flex min-w-0 flex-1 items-center">
        <div class="flex min-w-0 items-center gap-2.5">
          <span
            class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full border text-caption font-bold"
            :class="{
              'border-success bg-success text-white': stateFor(index) === 'done',
              'border-accent bg-accent-light text-accent-dark': stateFor(index) === 'current',
              'border-border-strong bg-surface text-ink-subtle': stateFor(index) === 'todo',
            }"
          >
            <Check v-if="stateFor(index) === 'done'" :size="14" aria-hidden="true" />
            <span v-else aria-hidden="true">{{ index + 1 }}</span>
            <span class="sr-only">
              {{ STATE_LABEL[stateFor(index)] }}: {{ step.label }}
            </span>
          </span>

          <span class="min-w-0">
            <span
              class="block truncate text-caption font-semibold"
              :class="stateFor(index) === 'todo' ? 'text-ink-subtle' : 'text-ink'"
            >
              {{ step.label }}
            </span>
            <span class="sr-only">{{ STATE_LABEL[stateFor(index)] }}</span>
          </span>
        </div>

        <span
          v-if="index < steps.length - 1"
          class="mx-2.5 h-px min-w-[1rem] flex-1"
          :class="stateFor(index) === 'done' ? 'bg-success' : 'bg-border'"
          aria-hidden="true"
        />
      </li>
    </ol>

    <!-- Mobile: compact progress -->
    <div class="sm:hidden">
      <div class="flex items-baseline justify-between gap-3">
        <p class="text-label font-semibold text-ink">
          Step {{ safeCurrent }} of {{ total }} · {{ steps[safeCurrent - 1]?.label }}
        </p>
        <p class="text-caption tabular-nums text-ink-subtle">{{ percent }}%</p>
      </div>
      <div
        class="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-border"
        role="progressbar"
        :aria-valuenow="safeCurrent"
        :aria-valuemin="1"
        :aria-valuemax="total"
        :aria-label="`Step ${safeCurrent} of ${total}: ${steps[safeCurrent - 1]?.label}`"
      >
        <div class="h-full rounded-full bg-primary transition-all duration-200" :style="{ width: `${percent}%` }" />
      </div>
    </div>
  </nav>
</template>
