<script setup lang="ts">
import { computed, useId } from 'vue'

const props = withDefaults(
  defineProps<{
    modelValue: string | number
    label: string
    type?: string
    placeholder?: string
    hint?: string
    error?: string
    required?: boolean
    disabled?: boolean
    autocomplete?: string
    inputmode?: 'text' | 'numeric' | 'decimal' | 'email' | 'tel'
    maxlength?: number
    min?: number | string
    step?: number | string
  }>(),
  {
    type: 'text',
    placeholder: undefined,
    hint: undefined,
    error: undefined,
    required: false,
    disabled: false,
    autocomplete: undefined,
    inputmode: undefined,
    maxlength: undefined,
    min: undefined,
    step: undefined,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
  blur: [event: FocusEvent]
}>()

const uid = useId()
const inputId = computed(() => `input-${uid}`)
const describedBy = computed(() => {
  const ids: string[] = []
  if (props.hint) ids.push(`${inputId.value}-hint`)
  if (props.error) ids.push(`${inputId.value}-error`)
  return ids.length ? ids.join(' ') : undefined
})

function onInput(event: Event): void {
  const target = event.target as HTMLInputElement
  emit('update:modelValue', target.value)
}
</script>

<template>
  <div class="w-full">
    <label :for="inputId" class="fin-label">
      {{ label }}
      <span v-if="required" class="text-danger" aria-hidden="true">*</span>
      <span v-if="!required" class="text-ink-subtle font-normal">(optional)</span>
    </label>

    <slot name="prefix" />

    <div class="relative">
      <slot name="prefixInner" />

      <input
        :id="inputId"
        :type="type"
        :value="modelValue"
        :placeholder="placeholder"
        :disabled="disabled"
        :required="required"
        :autocomplete="autocomplete"
        :inputmode="inputmode"
        :maxlength="maxlength"
        :min="min"
        :step="step"
        :aria-invalid="error ? 'true' : undefined"
        :aria-describedby="describedBy"
        class="fin-input"
        :class="[error ? 'fin-input-error pr-10' : '', $slots.suffix ? 'pr-11' : '']"
        @input="onInput"
        @blur="emit('blur', $event)"
      />

      <div
        v-if="$slots.suffix"
        class="absolute inset-y-0 right-0 flex items-center pr-3 text-ink-subtle"
      >
        <slot name="suffix" />
      </div>
    </div>

    <p v-if="error" :id="`${inputId}-error`" class="fin-error-text" role="alert">
      <span aria-hidden="true">⚠</span>
      <span>{{ error }}</span>
    </p>
    <p v-else-if="hint" :id="`${inputId}-hint`" class="fin-hint">{{ hint }}</p>
  </div>
</template>
