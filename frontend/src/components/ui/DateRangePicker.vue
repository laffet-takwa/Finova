<script setup lang="ts">
import { computed } from 'vue'
import { CalendarRange } from 'lucide-vue-next'

const props = withDefaults(
  defineProps<{
    from: string
    to: string
    label?: string
    presets?: Array<{ label: string; days: number }>
  }>(),
  {
    label: 'Date range',
    presets: () => [
      { label: '7 days', days: 7 },
      { label: '30 days', days: 30 },
      { label: '90 days', days: 90 },
    ],
  },
)

const emit = defineEmits<{ 'update:from': [value: string]; 'update:to': [value: string] }>()

const maxToday = computed(() => new Date().toISOString().slice(0, 10))

function applyPreset(days: number): void {
  const to = new Date()
  const from = new Date()
  from.setDate(to.getDate() - days)
  emit('update:from', from.toISOString().slice(0, 10))
  emit('update:to', to.toISOString().slice(0, 10))
}

function clear(): void {
  emit('update:from', '')
  emit('update:to', '')
}
</script>

<template>
  <fieldset class="min-w-0">
    <legend class="fin-label flex items-center gap-1.5">
      <CalendarRange :size="13" aria-hidden="true" />
      {{ label }}
    </legend>

    <div class="flex flex-wrap items-center gap-2">
      <div class="flex items-center gap-1.5">
        <label :for="`range-from-${label}`" class="sr-only">From date</label>
        <input
          :id="`range-from-${label}`"
          type="date"
          :value="from"
          :max="to || maxToday"
          class="fin-input h-10 w-[9.5rem] px-2.5 text-[0.8125rem]"
          @change="emit('update:from', ($event.target as HTMLInputElement).value)"
        />
        <span class="text-ink-subtle" aria-hidden="true">–</span>
        <label :for="`range-to-${label}`" class="sr-only">To date</label>
        <input
          :id="`range-to-${label}`"
          type="date"
          :value="to"
          :max="maxToday"
          class="fin-input h-10 w-[9.5rem] px-2.5 text-[0.8125rem]"
          @change="emit('update:to', ($event.target as HTMLInputElement).value)"
        />
      </div>

      <div class="flex items-center gap-1">
        <button
          v-for="preset in presets"
          :key="preset.days"
          type="button"
          class="h-8 rounded-md border border-border px-2.5 text-caption font-medium text-ink-muted transition-colors hover:border-border-strong hover:bg-surface-sunken hover:text-ink"
          @click="applyPreset(preset.days)"
        >
          {{ preset.label }}
        </button>
        <button
          v-if="from || to"
          type="button"
          class="h-8 rounded-md px-2 text-caption font-medium text-ink-subtle transition-colors hover:text-ink"
          @click="clear"
        >
          Clear
        </button>
      </div>
    </div>
  </fieldset>
</template>