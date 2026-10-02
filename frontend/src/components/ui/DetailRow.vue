<script setup lang="ts">
withDefaults(
  defineProps<{
    label: string
    value?: string
    /** Renders the value in the mono face — used for references and account numbers. */
    mono?: boolean
    hint?: string
    /** Emphasised rows (fees, totals, headline amounts) get larger type. */
    emphasis?: boolean
  }>(),
  { value: undefined, mono: false, hint: undefined, emphasis: false },
)
</script>

<template>
  <div class="flex flex-col gap-1 py-3 sm:flex-row sm:items-baseline sm:justify-between sm:gap-6">
    <dt class="text-caption font-semibold uppercase tracking-wider text-ink-subtle sm:pt-0.5">
      {{ label }}
    </dt>
    <dd class="min-w-0 sm:text-right">
      <span
        v-if="value"
        class="block break-words text-ink"
        :class="[
          mono ? 'font-mono text-[0.8125rem]' : emphasis ? 'fin-amount text-[1.125rem]' : 'text-[0.9375rem]',
        ]"
      >
        <slot>{{ value }}</slot>
      </span>
      <span v-else class="text-[0.9375rem] text-ink-subtle">—</span>
      <span v-if="hint" class="mt-0.5 block text-caption text-ink-subtle">{{ hint }}</span>
    </dd>
  </div>
</template>
