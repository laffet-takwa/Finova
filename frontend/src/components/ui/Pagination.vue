<script setup lang="ts">
import { computed } from 'vue'
import { ChevronLeft, ChevronRight } from 'lucide-vue-next'

const props = withDefaults(
  defineProps<{
    page: number
    totalPages: number
    totalElements: number
    pageSize: number
    label?: string
  }>(),
  { label: 'results' },
)

const emit = defineEmits<{ change: [page: number]; sizeChange: [size: number] }>()

const from = computed(() => (props.totalElements === 0 ? 0 : props.page * props.pageSize + 1))
const to = computed(() => Math.min((props.page + 1) * props.pageSize, props.totalElements))

const pages = computed<(number | 'gap')[]>(() => {
  const total = props.totalPages
  if (total <= 7) return Array.from({ length: total }, (_, index) => index)
  const current = props.page
  const items: (number | 'gap')[] = [0]
  const start = Math.max(1, current - 1)
  const end = Math.min(total - 2, current + 1)
  if (start > 1) items.push('gap')
  for (let index = start; index <= end; index += 1) items.push(index)
  if (end < total - 2) items.push('gap')
  items.push(total - 1)
  return items
})

function go(target: number): void {
  if (target < 0 || target >= props.totalPages || target === props.page) return
  emit('change', target)
}
</script>

<template>
  <nav
    class="flex flex-col items-center justify-between gap-3 sm:flex-row"
    aria-label="Pagination"
  >
    <p class="text-caption text-ink-muted" aria-live="polite">
      <template v-if="totalElements > 0">
        Showing <span class="font-semibold text-ink">{{ from }}–{{ to }}</span> of
        <span class="font-semibold text-ink">{{ totalElements.toLocaleString('en-US') }}</span>
        {{ label }}
      </template>
      <template v-else>No {{ label }}</template>
    </p>

    <div class="flex items-center gap-3">
      <label class="hidden items-center gap-2 text-caption text-ink-muted sm:flex">
        <span>Rows</span>
        <select
          class="h-9 rounded-md border border-border bg-surface px-2 text-caption text-ink"
          :value="pageSize"
          @change="emit('sizeChange', Number(($event.target as HTMLSelectElement).value))"
        >
          <option v-for="size in [10, 20, 50]" :key="size" :value="size">{{ size }}</option>
        </select>
      </label>

      <div v-if="totalPages > 1" class="flex items-center gap-1">
        <button
          type="button"
          class="flex h-9 w-9 items-center justify-center rounded-md border border-border text-ink-muted transition-colors hover:border-border-strong hover:bg-surface-sunken hover:text-ink disabled:opacity-40 disabled:pointer-events-none"
          :disabled="page === 0"
          aria-label="Previous page"
          @click="go(page - 1)"
        >
          <ChevronLeft :size="16" aria-hidden="true" />
        </button>

        <template v-for="(item, index) in pages" :key="`${item}-${index}`">
          <span v-if="item === 'gap'" class="px-1 text-ink-subtle" aria-hidden="true">…</span>
          <button
            v-else
            type="button"
            class="h-9 min-w-[2.25rem] rounded-md px-2 text-[0.8125rem] font-semibold tabular-nums transition-colors"
            :class="
              item === page
                ? 'bg-primary text-white'
                : 'border border-border text-ink-muted hover:border-border-strong hover:bg-surface-sunken hover:text-ink'
            "
            :aria-current="item === page ? 'page' : undefined"
            :aria-label="`Page ${item + 1}`"
            @click="go(item)"
          >
            {{ item + 1 }}
          </button>
        </template>

        <button
          type="button"
          class="flex h-9 w-9 items-center justify-center rounded-md border border-border text-ink-muted transition-colors hover:border-border-strong hover:bg-surface-sunken hover:text-ink disabled:opacity-40 disabled:pointer-events-none"
          :disabled="page >= totalPages - 1"
          aria-label="Next page"
          @click="go(page + 1)"
        >
          <ChevronRight :size="16" aria-hidden="true" />
        </button>
      </div>
    </div>
  </nav>
</template>