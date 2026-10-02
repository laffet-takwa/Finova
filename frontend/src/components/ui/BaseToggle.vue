<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    modelValue: boolean
    label: string
    description?: string
    disabled?: boolean
  }>(),
  { description: undefined, disabled: false },
)

const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
</script>

<template>
  <div class="flex items-start justify-between gap-6 py-3.5">
    <div class="min-w-0">
      <label :for="`toggle-${label}`" class="block text-[0.9375rem] font-medium text-ink cursor-pointer">
        {{ props.label }}
      </label>
      <p v-if="props.description" class="mt-1 text-caption text-ink-muted">{{ props.description }}</p>
    </div>

    <button
      :id="`toggle-${label}`"
      type="button"
      role="switch"
      :aria-checked="props.modelValue"
      :aria-label="props.label"
      :disabled="props.disabled"
      class="relative inline-flex h-6 w-11 shrink-0 items-center rounded-full transition-colors duration-200
        disabled:opacity-50 disabled:cursor-not-allowed"
      :class="props.modelValue ? 'bg-primary' : 'bg-border-strong'"
      @click="emit('update:modelValue', !props.modelValue)"
    >
      <span
        class="inline-block h-4.5 w-4.5 h-[18px] w-[18px] transform rounded-full bg-white shadow-sm transition-transform duration-200"
        :class="props.modelValue ? 'translate-x-[24px]' : 'translate-x-[3px]'"
      />
    </button>
  </div>
</template>