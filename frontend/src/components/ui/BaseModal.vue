<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { X } from 'lucide-vue-next'

const props = withDefaults(
  defineProps<{
    open: boolean
    title: string
    description?: string
    size?: 'sm' | 'md' | 'lg' | 'xl'
    dismissable?: boolean
  }>(),
  { description: undefined, size: 'md', dismissable: true },
)

const emit = defineEmits<{ close: [] }>()

const panel = ref<HTMLElement | null>(null)
let previouslyFocused: HTMLElement | null = null

const widths: Record<string, string> = {
  sm: 'max-w-sm',
  md: 'max-w-lg',
  lg: 'max-w-2xl',
  xl: 'max-w-4xl',
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape' && props.dismissable) {
    event.stopPropagation()
    emit('close')
    return
  }
  if (event.key !== 'Tab' || !panel.value) return

  const focusable = panel.value.querySelectorAll<HTMLElement>(
    'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
  )
  if (focusable.length === 0) return
  const first = focusable[0]
  const last = focusable[focusable.length - 1]

  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}

watch(
  () => props.open,
  async (isOpen) => {
    if (isOpen) {
      previouslyFocused = document.activeElement as HTMLElement | null
      document.body.style.overflow = 'hidden'
      document.addEventListener('keydown', onKeydown)
      await Promise.resolve()
      const target = panel.value?.querySelector<HTMLElement>('[data-autofocus]') ?? panel.value
      target?.focus()
    } else {
      document.body.style.overflow = ''
      document.removeEventListener('keydown', onKeydown)
      previouslyFocused?.focus()
      previouslyFocused = null
    }
  },
)

onMounted(() => {
  if (props.open) document.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = ''
})
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div
        v-if="open"
        class="fixed inset-0 z-50 flex items-end justify-center sm:items-center"
        role="dialog"
        aria-modal="true"
        :aria-labelledby="`modal-title-${title.replace(/\s/g, '-')}`"
      >
        <div
          class="absolute inset-0 bg-primary-dark/45 backdrop-blur-[2px]"
          aria-hidden="true"
          @click="dismissable && $emit('close')"
        />

        <div
          ref="panel"
          tabindex="-1"
          class="relative w-full rounded-t-xl bg-surface shadow-overlay animate-slide-up sm:rounded-xl"
          :class="widths[size]"
        >
          <header
            v-if="$slots.header || title"
            class="flex items-start justify-between gap-4 border-b border-border px-5 py-4 sm:px-6"
          >
            <div class="min-w-0">
              <h2
                :id="`modal-title-${title.replace(/\s/g, '-')}`"
                class="text-headline text-ink"
              >
                {{ title }}
              </h2>
              <p v-if="description" class="mt-1 text-[0.875rem] text-ink-muted">{{ description }}</p>
            </div>
            <button
              v-if="dismissable"
              type="button"
              class="-mr-1 -mt-1 flex h-9 w-9 shrink-0 items-center justify-center rounded-md text-ink-subtle transition-colors hover:bg-surface-sunken hover:text-ink"
              aria-label="Close dialog"
              @click="$emit('close')"
            >
              <X :size="18" aria-hidden="true" />
            </button>
          </header>

          <div class="fin-scroll-thin max-h-[70vh] overflow-y-auto px-5 py-5 sm:px-6">
            <slot />
          </div>

          <footer
            v-if="$slots.footer"
            class="flex flex-col-reverse gap-2 border-t border-border bg-surface-sunken px-5 py-4 sm:flex-row sm:justify-end sm:px-6"
          >
            <slot name="footer" />
          </footer>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.modal-enter-active,
.modal-leave-active {
  transition: opacity 160ms ease-out;
}

.modal-enter-active > div:last-child,
.modal-leave-active > div:last-child {
  transition: transform 180ms ease-out, opacity 180ms ease-out;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

.modal-enter-from > div:last-child,
.modal-leave-to > div:last-child {
  transform: translateY(12px);
  opacity: 0;
}

@media (prefers-reduced-motion: reduce) {
  .modal-enter-active,
  .modal-leave-active,
  .modal-enter-active > div:last-child,
  .modal-leave-active > div:last-child {
    transition: none;
  }
}
</style>