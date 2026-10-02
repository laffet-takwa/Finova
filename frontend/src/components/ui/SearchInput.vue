<script setup lang="ts">
import { computed } from 'vue'
import { Search, X } from 'lucide-vue-next'

const props = withDefaults(
  defineProps<{
    modelValue: string
    placeholder?: string
    label?: string
    debounceMs?: number
    clearable?: boolean
  }>(),
  { placeholder: 'Search', label: 'Search', debounceMs: 300, clearable: true },
)

const emit = defineEmits<{ 'update:modelValue': [value: string]; search: [value: string] }>()

let timer: number | null = null

const hasValue = computed(() => props.modelValue.trim().length > 0)

function onInput(event: Event): void {
  const value = (event.target as HTMLInputElement).value
  emit('update:modelValue', value)
  if (timer !== null) window.clearTimeout(timer)
  timer = window.setTimeout(() => emit('search', value), props.debounceMs)
}

function clear(): void {
  emit('update:modelValue', '')
  emit('search', '')
}
</script>

<template>
  <div class="relative w-full">
    <label :for="`search-${label}`" class="sr-only">{{ label }}</label>
    <Search
      :size="16"
      class="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-ink-subtle"
      aria-hidden="true"
    />

    <input
      :id="`search-${label}`"
      type="search"
      :value="modelValue"
      :placeholder="placeholder"
      class="fin-input pl-9"
      :class="{ 'pr-9': hasValue && clearable }"
      autocomplete="off"
      @input="onInput"
      @keydown.enter="emit('search', modelValue)"
    />

    <button
      v-if="hasValue && clearable"
      type="button"
      class="absolute right-2 top-1/2 flex h-7 w-7 -translate-y-1/2 items-center justify-center rounded text-ink-subtle transition-colors hover:bg-surface-sunken hover:text-ink"
      :aria-label="`Clear ${label.toLowerCase()}`"
      @click="clear"
    >
      <X :size="14" aria-hidden="true" />
    </button>
  </div>
</template>