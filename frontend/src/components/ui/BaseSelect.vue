<script setup lang="ts">
import { computed, useId } from 'vue'
import { ChevronDown } from 'lucide-vue-next'

export interface SelectOption {
  value: string
  label: string
  description?: string
}

const props = withDefaults(
  defineProps<{
    modelValue: string
    label: string
    options: SelectOption[]
    placeholder?: string
    hint?: string
    error?: string
    disabled?: boolean
    required?: boolean
    clearable?: boolean
  }>(),
  {
    placeholder: 'Select an option',
    hint: undefined,
    error: undefined,
    disabled: false,
    required: false,
    clearable: false,
  },
)

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const uid = useId()
const selectId = computed(() => `select-${uid}`)
const describedBy = computed(() => (props.error ? `${selectId.value}-error` : props.hint ? `${selectId.value}-hint` : undefined))
const isPlaceholderSelected = computed(() => !props.modelValue)
</script>

<template>
  <div class="w-full">
    <label :for="selectId" class="fin-label">
      {{ label }}
      <span v-if="required" class="text-danger" aria-hidden="true">*</span>
    </label>

    <div class="relative">
      <select
        :id="selectId"
        :value="modelValue"
        :disabled="disabled"
        :required="required"
        :aria-invalid="error ? 'true' : undefined"
        :aria-describedby="describedBy"
        class="fin-input appearance-none pr-10 cursor-pointer"
        :class="{ 'fin-input-error': error, 'text-ink-subtle': isPlaceholderSelected }"
        @change="emit('update:modelValue', ($event.target as HTMLSelectElement).value)"
      >
        <option value="" disabled>{{ placeholder }}</option>
        <option v-for="option in options" :key="option.value" :value="option.value">
          {{ option.label }}
        </option>
      </select>

      <ChevronDown
        :size="16"
        class="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-ink-subtle"
        aria-hidden="true"
      />
    </div>

    <p v-if="error" :id="`${selectId}-error`" class="fin-error-text" role="alert">
      <span aria-hidden="true">⚠</span>
      <span>{{ error }}</span>
    </p>
    <p v-else-if="hint" :id="`${selectId}-hint`" class="fin-hint">{{ hint }}</p>
  </div>
</template>
