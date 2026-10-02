<script setup lang="ts">
import { computed, useId } from 'vue'
import { tabPanelId, type TabItem } from '@/components/ui/tabs'

const props = withDefaults(
  defineProps<{
    tabs: TabItem[]
    modelValue: string
    ariaLabel: string
    /**
     * `vertical` renders a stacked switcher on desktop that becomes a horizontal
     * scroller on mobile. `horizontal` renders one horizontal strip at every width.
     */
    layout?: 'vertical' | 'horizontal'
  }>(),
  { layout: 'horizontal' },
)

const emit = defineEmits<{ 'update:modelValue': [id: string] }>()

const uid = useId()

const selectedId = computed(() =>
  props.tabs.some((tab) => tab.id === props.modelValue)
    ? props.modelValue
    : (props.tabs[0]?.id ?? ''),
)

function tabId(id: string, scope: 'v' | 'h'): string {
  return `tab-${scope}-${uid}-${id}`
}

function panelId(id: string): string {
  return tabPanelId(props.ariaLabel, id)
}

function select(id: string): void {
  if (id !== selectedId.value) emit('update:modelValue', id)
}

/** Shared roving-focus behaviour for both rendered tab strips. */
function focusTab(index: number): void {
  const total = props.tabs.length
  if (total === 0) return
  const next = ((index % total) + total) % total
  const target = props.tabs[next]
  if (!target) return
  select(target.id)
  // Only one strip is visible at a given width, so whichever id resolves wins.
  const element =
    document.getElementById(tabId(target.id, 'v')) ?? document.getElementById(tabId(target.id, 'h'))
  element?.focus()
}

function onKeydown(event: KeyboardEvent): void {
  const index = props.tabs.findIndex((tab) => tab.id === selectedId.value)
  if (index === -1) return
  const vertical = props.layout === 'vertical'
  const nextKey = vertical ? 'ArrowDown' : 'ArrowRight'
  const prevKey = vertical ? 'ArrowUp' : 'ArrowLeft'

  switch (event.key) {
    case nextKey:
      event.preventDefault()
      focusTab(index + 1)
      break
    case prevKey:
      event.preventDefault()
      focusTab(index - 1)
      break
    case 'Home':
      event.preventDefault()
      focusTab(0)
      break
    case 'End':
      event.preventDefault()
      focusTab(props.tabs.length - 1)
      break
    default:
      break
  }
}

function tabClasses(id: string): string {
  return id === selectedId.value
    ? 'bg-primary-soft text-primary font-semibold'
    : 'text-ink-muted font-medium hover:bg-surface-sunken hover:text-ink'
}
</script>

<template>
  <div class="min-w-0">
    <!--
      Desktop switcher for `layout="vertical"`. `hidden` keeps the duplicate tab
      stops out of the tab order and the accessibility tree on small screens.
    -->
    <div
      v-if="layout === 'vertical'"
      class="hidden flex-col gap-1 lg:flex"
      role="tablist"
      :aria-label="ariaLabel"
      aria-orientation="vertical"
      @keydown="onKeydown"
    >
      <button
        v-for="tab in tabs"
        :key="`v-${tab.id}`"
        :id="tabId(tab.id, 'v')"
        type="button"
        role="tab"
        :aria-selected="tab.id === selectedId"
        :aria-controls="panelId(tab.id)"
        :tabindex="tab.id === selectedId ? 0 : -1"
        class="flex w-full items-center justify-between gap-3 rounded-md px-3 py-2.5 text-left text-[0.9375rem] transition-colors"
        :class="tabClasses(tab.id)"
        @click="select(tab.id)"
      >
        <span class="truncate">{{ tab.label }}</span>
        <span
          v-if="tab.count"
          class="flex h-5 min-w-[1.25rem] shrink-0 items-center justify-center rounded-full bg-primary px-1.5 text-[0.6875rem] font-bold text-white"
        >
          {{ tab.count > 99 ? '99+' : tab.count }}
        </span>
      </button>
    </div>

    <!-- Mobile strip (and the only strip for `layout="horizontal"`). -->
    <div
      class="fin-scroll-thin flex gap-1 overflow-x-auto pb-1"
      :class="layout === 'vertical' ? 'lg:hidden' : ''"
      role="tablist"
      :aria-label="ariaLabel"
      aria-orientation="horizontal"
      @keydown="onKeydown"
    >
      <button
        v-for="tab in tabs"
        :key="tab.id"
        :id="tabId(tab.id, 'h')"
        type="button"
        role="tab"
        :aria-selected="tab.id === selectedId"
        :aria-controls="panelId(tab.id)"
        :tabindex="tab.id === selectedId ? 0 : -1"
        class="flex shrink-0 items-center gap-2 whitespace-nowrap rounded-md px-3 py-2 text-[0.875rem] transition-colors"
        :class="tabClasses(tab.id)"
        @click="select(tab.id)"
      >
        <span>{{ tab.label }}</span>
        <span
          v-if="tab.count"
          class="flex h-5 min-w-[1.25rem] items-center justify-center rounded-full bg-primary px-1.5 text-[0.6875rem] font-bold text-white"
        >
          {{ tab.count > 99 ? '99+' : tab.count }}
        </span>
        <span v-if="tab.count" class="sr-only">{{ tab.count }} unread</span>
      </button>
    </div>
  </div>
</template>
