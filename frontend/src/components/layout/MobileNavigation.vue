<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import {
  ArrowUpRight,
  Bell,
  LayoutDashboard,
  Receipt,
  ShieldAlert,
  Users,
  Wallet,
} from 'lucide-vue-next'
import { useNotificationStore } from '@/stores/notificationStore'

const route = useRoute()
const notifications = useNotificationStore()

const links = computed(() =>
  route.path.startsWith('/admin')
    ? [
        { label: 'Overview', to: '/admin', icon: LayoutDashboard, badge: 0 },
        { label: 'Users', to: '/admin/users', icon: Users, badge: 0 },
        { label: 'Transfers', to: '/admin/transactions', icon: Receipt, badge: 0 },
        { label: 'Risk', to: '/admin/fraud', icon: ShieldAlert, badge: notifications.unreadCount },
      ]
    : [
        { label: 'Home', to: '/dashboard', icon: LayoutDashboard, badge: 0 },
        { label: 'Accounts', to: '/accounts', icon: Wallet, badge: 0 },
        { label: 'Send', to: '/transfer', icon: ArrowUpRight, badge: 0 },
        { label: 'Activity', to: '/transactions', icon: Receipt, badge: 0 },
        {
          label: 'Alerts',
          to: '/notifications',
          icon: Bell,
          badge: notifications.unreadCount,
        },
      ],
)

function isActive(to: string): boolean {
  if (to === '/admin') return route.path === '/admin'
  return route.path === to || route.path.startsWith(`${to}/`)
}
</script>

<template>
  <nav
    class="fixed inset-x-0 bottom-0 z-30 border-t border-border bg-surface/97 backdrop-blur lg:hidden fin-safe-bottom"
    aria-label="Primary"
  >
    <ul class="grid grid-cols-5">
      <li v-for="link in links" :key="link.to">
        <RouterLink
          :to="link.to"
          class="relative flex flex-col items-center gap-1 px-1 pb-2 pt-2.5 text-[0.625rem] font-semibold transition-colors"
          :class="isActive(link.to) ? 'text-primary' : 'text-ink-subtle'"
          :aria-current="isActive(link.to) ? 'page' : undefined"
        >
          <span
            class="flex h-7 w-12 items-center justify-center rounded-md transition-colors"
            :class="isActive(link.to) ? 'bg-primary-soft' : ''"
          >
            <component :is="link.icon" :size="18" aria-hidden="true" />
          </span>
          <span class="truncate">{{ link.label }}</span>
          <span
            v-if="link.badge > 0"
            class="absolute right-[22%] top-1 flex h-3.5 min-w-[14px] items-center justify-center rounded-full bg-danger px-1 text-[0.5rem] font-bold text-white"
            aria-hidden="true"
          >
            {{ link.badge > 9 ? '9+' : link.badge }}
          </span>
        </RouterLink>
      </li>
    </ul>
  </nav>
</template>