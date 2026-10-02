<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { Check, Copy } from 'lucide-vue-next'

const props = withDefaults(
  defineProps<{
    /** Text placed on the clipboard. */
    value: string
    /** Describes what is copied, e.g. "Account number TN58 1000 …". Used as the accessible name. */
    label: string
    size?: 'sm' | 'md'
  }>(),
  { size: 'md' },
)

const copied = ref(false)
let timer: number | null = null

async function copy(): Promise<void> {
  if (props.value.trim().length === 0) return
  try {
    await navigator.clipboard.writeText(props.value.trim())
    copied.value = true
    if (timer !== null) window.clearTimeout(timer)
    timer = window.setTimeout(() => {
      copied.value = false
    }, 2200)
  } catch {
    // Clipboard access can be refused; say so rather than pretending it worked.
    copied.value = false
  }
}

onBeforeUnmount(() => {
  if (timer !== null) window.clearTimeout(timer)
})
</script>

<template>
  <button
    type="button"
    class="inline-flex items-center gap-1.5 rounded-md border border-border text-ink-subtle transition-colors hover:border-border-strong hover:bg-surface-sunken hover:text-ink"
    :class="size === 'sm' ? 'h-7 px-2 text-caption' : 'h-9 px-2.5 text-[0.8125rem]'"
    :aria-label="copied ? `${label} copied to clipboard` : `Copy ${label}`"
    @click="copy"
  >
    <Check v-if="copied" :size="size === 'sm' ? 13 : 15" class="text-success" aria-hidden="true" />
    <Copy v-else :size="size === 'sm' ? 13 : 15" aria-hidden="true" />
    <span :class="copied ? 'text-success' : ''">{{ copied ? 'Copied' : 'Copy' }}</span>
  </button>
</template>