<script setup lang="ts">
withDefaults(defineProps<{ eyebrow?: string; title: string; description?: string }>(), {
  eyebrow: undefined,
  description: undefined,
})
</script>

<template>
  <header class="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
    <div class="min-w-0">
      <p
        v-if="eyebrow"
        class="mb-1.5 text-caption font-semibold uppercase tracking-[0.14em] text-primary"
      >
        {{ eyebrow }}
      </p>
      <h1 class="text-title text-ink">{{ title }}</h1>
      <p v-if="description" class="mt-1.5 max-w-2xl text-[0.9375rem] leading-relaxed text-ink-muted">
        {{ description }}
      </p>
    </div>

    <div v-if="$slots.actions" class="flex shrink-0 flex-wrap items-center gap-2">
      <slot name="actions" />
    </div>
  </header>
</template>