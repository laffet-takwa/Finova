<script setup lang="ts">
import { computed, useAttrs } from 'vue'
import { AlertTriangle, RefreshCw, WifiOff } from 'lucide-vue-next'

const props = withDefaults(
  defineProps<{
    title?: string
    description?: string
    retryLabel?: string
    offline?: boolean
    compact?: boolean
    correlationId?: string
  }>(),
  {
    title: 'Something went wrong',
    description: undefined,
    retryLabel: 'Try again',
    offline: false,
    compact: false,
    correlationId: undefined,
  },
)

defineEmits<{ retry: [] }>()

const attrs = useAttrs()

/**
 * The retry button only appears when the host actually listens for `retry`.
 * Gating it on slot content (as this once did) hid the button on every view,
 * leaving every error state unactionable.
 */
const canRetry = computed(() => typeof attrs.onRetry === 'function')

const icon = computed(() => (props.offline ? WifiOff : AlertTriangle))
</script>

<template>
  <div
    class="flex flex-col items-center justify-center text-center"
    :class="compact ? 'py-10 px-4' : 'py-14 px-6'"
    role="alert"
    aria-live="assertive"
  >
    <div
      class="mb-4 flex h-12 w-12 items-center justify-center rounded-full"
      :class="offline ? 'bg-warning-light' : 'bg-danger-light'"
      aria-hidden="true"
    >
      <component :is="icon" :size="22" :class="offline ? 'text-warning-dark' : 'text-danger'" />
    </div>

    <h3 class="text-headline text-ink">{{ title }}</h3>

    <p class="mt-2 max-w-md text-[0.9375rem] leading-relaxed text-ink-muted">
      {{ description ?? (offline ? 'You appear to be offline. Check your connection and try again.' : "We couldn't complete this request. Please try again.") }}
    </p>

    <p v-if="correlationId" class="mt-3 font-mono text-[0.6875rem] text-ink-subtle">
      Reference: {{ correlationId }}
    </p>

    <div class="mt-6 flex flex-wrap items-center justify-center gap-3">
      <button v-if="canRetry" type="button" class="fin-btn-secondary" @click="$emit('retry')">
        <RefreshCw :size="15" aria-hidden="true" />
        {{ retryLabel }}
      </button>
      <slot />
    </div>
  </div>
</template>