<script setup lang="ts">
import { Inbox } from 'lucide-vue-next'

withDefaults(
  defineProps<{
    title: string
    description?: string
    icon?: typeof Inbox
    actionLabel?: string
    compact?: boolean
  }>(),
  { description: undefined, icon: Inbox, actionLabel: undefined, compact: false },
)

defineEmits<{ action: [] }>()
</script>

<template>
  <div
    class="flex flex-col items-center justify-center text-center"
    :class="compact ? 'py-10 px-4' : 'py-16 px-6'"
    role="status"
  >
    <div
      class="mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-primary-soft"
      aria-hidden="true"
    >
      <component :is="icon" :size="22" class="text-primary" />
    </div>

    <h3 class="text-headline text-ink">{{ title }}</h3>

    <p v-if="description" class="mt-2 max-w-sm text-[0.9375rem] leading-relaxed text-ink-muted">
      {{ description }}
    </p>

    <div v-if="$slots.default" class="mt-6">
      <slot />
    </div>

    <button v-if="actionLabel" type="button" class="fin-btn-primary mt-6" @click="$emit('action')">
      {{ actionLabel }}
    </button>
  </div>
</template>