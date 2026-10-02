<script setup lang="ts">
import { computed } from 'vue'
import { Loader2 } from 'lucide-vue-next'

type Variant = 'primary' | 'accent' | 'secondary' | 'ghost' | 'danger'
type Size = 'sm' | 'md' | 'lg'

const props = withDefaults(
  defineProps<{
    variant?: Variant
    size?: Size
    type?: 'button' | 'submit' | 'reset'
    loading?: boolean
    disabled?: boolean
    block?: boolean
    href?: string
    to?: string
    ariaLabel?: string
  }>(),
  {
    variant: 'primary',
    size: 'md',
    type: 'button',
    loading: false,
    disabled: false,
    block: false,
    href: undefined,
    to: undefined,
    ariaLabel: undefined,
  },
)

const variantClass = computed(() => {
  if (props.size === 'sm') {
    return {
      primary: 'bg-primary text-white hover:bg-primary-light',
      accent: 'bg-accent text-white hover:bg-accent-dark',
      secondary: 'bg-surface text-ink border border-border hover:bg-surface-sunken',
      ghost: 'text-ink-muted hover:bg-primary-soft hover:text-primary',
      danger: 'bg-danger text-white hover:bg-danger-dark',
    }[props.variant]
  }
  return {
    primary: 'fin-btn-primary',
    accent: 'fin-btn-accent',
    secondary: 'fin-btn-secondary',
    ghost: 'fin-btn-ghost',
    danger: 'fin-btn-danger',
  }[props.variant]
})

const sizeClass = computed(() => {
  if (props.size === 'sm') return 'h-9 px-3 text-[0.8125rem]'
  if (props.size === 'lg') return 'h-12 px-6 text-base'
  return ''
})
</script>

<template>
  <component
    :is="to ? 'RouterLink' : href ? 'a' : 'button'"
    :to="to"
    :href="href"
    :type="to || href ? undefined : type"
    :disabled="to || href ? undefined : disabled || loading"
    :aria-busy="loading || undefined"
    :aria-label="ariaLabel"
    class="fin-btn"
    :class="[variantClass, sizeClass, { 'w-full': block, 'opacity-55 pointer-events-none': disabled || loading }]"
  >
    <Loader2 v-if="loading" :size="16" class="animate-spin" aria-hidden="true" />
    <slot name="icon" />
    <slot />
    <slot name="trailing" />
  </component>
</template>
