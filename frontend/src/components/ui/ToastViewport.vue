<script setup lang="ts">
import { computed } from 'vue'
import { CheckCircle2, Info, TriangleAlert, X, XCircle } from 'lucide-vue-next'
import { useToastStore, type ToastVariant } from '@/stores/toastStore'

const toast = useToastStore()

const config: Record<ToastVariant, { icon: typeof Info; accent: string }> = {
  success: { icon: CheckCircle2, accent: 'text-success' },
  error: { icon: XCircle, accent: 'text-danger' },
  warning: { icon: TriangleAlert, accent: 'text-warning' },
  info: { icon: Info, accent: 'text-accent' },
}
</script>

<template>
  <div
    class="pointer-events-none fixed inset-x-0 bottom-0 z-[60] flex flex-col items-center gap-2 px-4 pb-4
      sm:inset-x-auto sm:right-0 sm:top-0 sm:items-end sm:px-6 sm:pb-0 sm:pt-6 fin-safe-bottom"
    role="region"
    aria-label="Notifications"
  >
    <TransitionGroup name="toast">
      <div
        v-for="item in toast.toasts"
        :key="item.id"
        class="pointer-events-auto flex w-full max-w-sm items-start gap-3 rounded-lg border border-border
          bg-surface p-3.5 shadow-overlay animate-slide-up"
        role="status"
        aria-live="polite"
      >
        <component
          :is="config[item.variant].icon"
          :size="19"
          class="mt-0.5 shrink-0"
          :class="config[item.variant].accent"
          aria-hidden="true"
        />

        <div class="min-w-0 flex-1">
          <p class="text-[0.9375rem] font-semibold leading-snug text-ink">{{ item.title }}</p>
          <p
            v-if="item.description"
            class="mt-0.5 break-words text-[0.8125rem] leading-relaxed text-ink-muted"
          >
            {{ item.description }}
          </p>
        </div>

        <button
          type="button"
          class="-mr-1 -mt-1 flex h-7 w-7 shrink-0 items-center justify-center rounded text-ink-subtle transition-colors hover:bg-surface-sunken hover:text-ink"
          :aria-label="`Dismiss notification: ${item.title}`"
          @click="toast.dismiss(item.id)"
        >
          <X :size="15" aria-hidden="true" />
        </button>
      </div>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.toast-enter-active,
.toast-leave-active {
  transition: opacity 180ms ease-out, transform 180ms ease-out;
}

.toast-enter-from {
  opacity: 0;
  transform: translateX(16px);
}

.toast-leave-to {
  opacity: 0;
  transform: translateX(16px) scale(0.98);
}

.toast-move {
  transition: transform 180ms ease-out;
}

@media (prefers-reduced-motion: reduce) {
  .toast-enter-active,
  .toast-leave-active,
  .toast-move {
    transition: none;
  }
}
</style>