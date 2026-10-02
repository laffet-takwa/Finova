import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { AppError } from '@/utils/errors'

export type ToastVariant = 'success' | 'error' | 'warning' | 'info'

export interface Toast {
  id: string
  title: string
  description?: string
  variant: ToastVariant
  timeout: number
}

const DEFAULT_TIMEOUTS: Record<ToastVariant, number> = {
  success: 4500,
  info: 4500,
  warning: 6500,
  error: 8000,
}

let counter = 0

export const useToastStore = defineStore('toast', () => {
  const toasts = ref<Toast[]>([])

  const hasToasts = computed(() => toasts.value.length > 0)

  function dismiss(id: string): void {
    toasts.value = toasts.value.filter((toast) => toast.id !== id)
  }

  function push(toast: Omit<Toast, 'id' | 'timeout'> & { timeout?: number }): string {
    counter += 1
    const id = `toast-${counter}`
    const entry: Toast = {
      id,
      title: toast.title,
      description: toast.description,
      variant: toast.variant,
      timeout: toast.timeout ?? DEFAULT_TIMEOUTS[toast.variant],
    }
    toasts.value = [...toasts.value, entry]
    if (entry.timeout > 0) {
      window.setTimeout(() => dismiss(id), entry.timeout)
    }
    return id
  }

  const success = (title: string, description?: string) => push({ title, description, variant: 'success' })
  const info = (title: string, description?: string) => push({ title, description, variant: 'info' })
  const warning = (title: string, description?: string) => push({ title, description, variant: 'warning' })
  const error = (title: string, description?: string) => push({ title, description, variant: 'error' })

  /** Surfaces an AppError with the correlation id so support can trace it. */
  function fromError(cause: unknown, fallbackTitle = 'Something went wrong'): void {
    if (cause instanceof AppError) {
      const suffix = cause.correlationId ? ` Reference: ${cause.correlationId}` : ''
      push({
        title: fallbackTitle,
        description: `${cause.message}${suffix}`,
        variant: cause.isNetworkError ? 'warning' : 'error',
      })
      return
    }
    const message = cause instanceof Error ? cause.message : 'An unexpected error occurred.'
    push({ title: fallbackTitle, description: message, variant: 'error' })
  }

  function clear(): void {
    toasts.value = []
  }

  return { toasts, hasToasts, push, success, info, warning, error, fromError, dismiss, clear }
})
