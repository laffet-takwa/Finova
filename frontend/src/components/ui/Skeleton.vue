<script setup lang="ts">
withDefaults(defineProps<{ variant?: 'text' | 'block'; rows?: number; width?: string }>(), {
  variant: 'text',
  rows: 1,
  width: '100%',
})
</script>

<template>
  <div
    v-if="variant === 'block'"
    class="space-y-3"
    role="status"
    aria-live="polite"
    aria-busy="true"
  >
    <div v-for="row in rows" :key="row" class="space-y-2">
      <div class="fin-skeleton h-3.5" :style="{ width: `${100 - (row - 1) * 12}%` }" />
      <div class="fin-skeleton h-3" :style="{ width: `${72 - (row - 1) * 8}%` }" />
    </div>
    <span class="sr-only">Loading content</span>
  </div>

  <span
    v-else
    class="fin-skeleton inline-block align-middle"
    :style="{ width, height: '1em' }"
    role="status"
    aria-live="polite"
    aria-busy="true"
  >
    <span class="sr-only">Loading</span>
  </span>
</template>