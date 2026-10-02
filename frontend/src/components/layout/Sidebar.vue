<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import * as icons from 'lucide-vue-next'
import {
  LayoutDashboard,
  Wallet,
  ArrowUpRight,
  Receipt,
  Bell,
  ShieldCheck,
  Settings,
  LifeBuoy,
  Users,
  ScrollText,
  ShieldAlert,
  ChevronsLeft,
  ChevronsRight,
} from 'lucide-vue-next'
import { ADMIN_NAV, CUSTOMER_NAV } from '@/router/navigation'
import { useNotificationStore } from '@/stores/notificationStore'

defineProps<{ collapsed: boolean }>()
const emit = defineEmits<{ toggle: []; navigate: [] }>()

const route = useRoute()
const notifications = useNotificationStore()

const sections = computed(() => (route.path.startsWith('/admin') ? ADMIN_NAV : CUSTOMER_NAV))

const ICONS: Record<string, unknown> = {
  LayoutDashboard,
  Wallet,
  ArrowUpRight,
  Receipt,
  Bell,
  ShieldCheck,
  Settings,
  LifeBuoy,
  Users,
  ScrollText,
  ShieldAlert,
}

function isActive(to: string): boolean {
  if (to === '/admin') return route.path === '/admin'
  if (to.includes('#')) return false
  return route.path === to || route.path.startsWith(`${to}/`)
}

function badgeFor(to: string): number {
  return to === '/notifications' ? notifications.unreadCount : 0
}

void icons
</script>

<template>
  <aside
    class="flex h-full flex-col border-r border-border bg-surface transition-[width] duration-200"
    :class="collapsed ? 'w-[4.5rem]' : 'w-64'"
    aria-label="Main navigation"
  >
    <div
      class="flex h-16 shrink-0 items-center gap-2.5 border-b border-border px-4"
      :class="collapsed ? 'justify-center px-0' : ''"
    >
      <RouterLink
        to="/dashboard"
        class="flex min-w-0 items-center gap-2.5 rounded-md"
        aria-label="Finova home"
        @click="emit('navigate')"
      >
        <span
          class="flex h-8 w-8 shrink-0 items-center justify-center rounded-md bg-primary"
          aria-hidden="true"
        >
          <svg viewBox="0 0 32 32" class="h-5 w-5" fill="none">
            <path
              d="M10 9.5v13M22 9.5v13M10 16h12"
              stroke="#FFFFFF"
              stroke-width="2.6"
              stroke-linecap="round"
            />
            <circle cx="22" cy="11.6" r="2.1" fill="#3B82F6" />
          </svg>
        </span>
        <span v-if="!collapsed" class="min-w-0">
          <span class="block font-display text-[1.0625rem] font-extrabold leading-none tracking-tight text-ink">
            FINOVA
          </span>
          <span class="mt-0.5 block truncate text-[0.625rem] font-medium leading-none text-ink-subtle">
            Digital Banking
          </span>
        </span>
      </RouterLink>
    </div>

    <nav class="fin-scroll-thin flex-1 overflow-y-auto px-3 py-4">
      <div v-for="section in sections" :key="section.title" class="mb-5 last:mb-0">
        <p
          v-if="!collapsed"
          class="mb-1.5 px-2.5 text-[0.625rem] font-bold uppercase tracking-[0.14em] text-ink-subtle"
        >
          {{ section.title }}
        </p>
        <div v-else class="mx-2 mb-2 border-t border-border" />

        <ul class="space-y-0.5">
          <li v-for="item in section.items" :key="item.to">
            <RouterLink
              :to="item.to"
              class="group flex items-center gap-3 rounded-md px-2.5 py-2 text-[0.875rem] font-medium transition-colors"
              :class="[
                isActive(item.to)
                  ? 'bg-primary-soft text-primary'
                  : 'text-ink-muted hover:bg-surface-sunken hover:text-ink',
                collapsed ? 'justify-center px-0' : '',
              ]"
              :title="collapsed ? item.label : undefined"
              :aria-current="isActive(item.to) ? 'page' : undefined"
              @click="emit('navigate')"
            >
              <component
                :is="ICONS[item.icon]"
                :size="18"
                class="shrink-0"
                :class="isActive(item.to) ? 'text-primary' : 'text-ink-subtle group-hover:text-ink'"
                aria-hidden="true"
              />
              <span v-if="!collapsed" class="min-w-0 flex-1 truncate">{{ item.label }}</span>
              <span
                v-if="!collapsed && badgeFor(item.to) > 0"
                class="flex h-4.5 min-w-[18px] shrink-0 items-center justify-center rounded-full bg-danger px-1 text-[0.625rem] font-bold text-white"
              >
                {{ badgeFor(item.to) > 99 ? '99+' : badgeFor(item.to) }}
              </span>
              <span
                v-else-if="collapsed && badgeFor(item.to) > 0"
                class="absolute right-2 top-1.5 h-2 w-2 rounded-full bg-danger ring-2 ring-surface"
                :aria-label="`${badgeFor(item.to)} unread`"
              />
            </RouterLink>
          </li>
        </ul>
      </div>
    </nav>

    <div class="shrink-0 border-t border-border p-3">
      <button
        type="button"
        class="hidden h-9 w-full items-center gap-2 rounded-md px-2.5 text-[0.8125rem] font-medium text-ink-subtle transition-colors hover:bg-surface-sunken hover:text-ink lg:flex"
        :class="collapsed ? 'justify-center px-0' : ''"
        :aria-label="collapsed ? 'Expand sidebar' : 'Collapse sidebar'"
        :aria-expanded="!collapsed"
        @click="emit('toggle')"
      >
        <ChevronsLeft v-if="!collapsed" :size="16" aria-hidden="true" />
        <ChevronsRight v-else :size="16" aria-hidden="true" />
        <span v-if="!collapsed">Collapse</span>
      </button>
    </div>
  </aside>
</template>