<script setup lang="ts">
import { computed } from 'vue'
import { SlidersHorizontal, X } from 'lucide-vue-next'

const props = withDefaults(
  defineProps<{
    activeCount?: number
    expandable?: boolean
    defaultOpen?: boolean
  }>(),
  { activeCount: 0, expandable: true, defaultOpen: false },
)

const open = defineModel<boolean>('open')
const emit = defineEmits<{ reset: [] }>()

const isOpen = computed(() => open.value ?? props.defaultOpen)
const hasFilters = computed(() => props.activeCount > 0)
</script>

<template>
  <section class="fin-card" aria-label="Filters">
    <div class="flex flex-wrap items-center gap-3 p-4">
      <div class="min-w-[12rem] flex-1">
        <slot name="search" />
      </div>

      <div v-if="$slots.primary" class="flex flex-wrap items-center gap-2">
        <slot name="primary" />
      </div>

      <button
        v-if="expandable && $slots.advanced"
        type="button"
        class="inline-flex h-10 items-center gap-2 rounded-md border border-border px-3 text-[0.8125rem] font-semibold text-ink-muted transition-colors hover:border-border-strong hover:bg-surface-sunken hover:text-ink"
        :aria-expanded="isOpen"
        aria-controls="filter-panel"
        @click="open = !isOpen"
      >
        <SlidersHorizontal :size="15" aria-hidden="true" />
        Filters
        <span
          v-if="hasFilters"
          class="flex h-4.5 min-w-[18px] items-center justify-center rounded-full bg-primary px-1 text-[0.625rem] font-bold text-white"
        >
          {{ activeCount }}
        </span>
      </button>

      <button
        v-if="hasFilters"
        type="button"
        class="inline-flex h-10 items-center gap-1.5 rounded-md px-2.5 text-[0.8125rem] font-medium text-ink-subtle transition-colors hover:text-danger"
        @click="emit('reset')"
      >
        <X :size="14" aria-hidden="true" />
        Reset
      </button>
    </div>

    <div
      v-show="isOpen && $slots.advanced"
      id="filter-panel"
      class="border-t border-border bg-surface-sunken px-4 py-4"
    >
      <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <slot name="advanced" />
      </div>
    </div>
  </section>
</template>