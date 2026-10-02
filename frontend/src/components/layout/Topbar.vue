<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  Bell,
  CheckCheck,
  LogOut,
  Menu,
  Search,
  ShieldCheck,
  UserCog,
} from 'lucide-vue-next'
import { useRouter, RouterLink } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useNotificationStore } from '@/stores/notificationStore'
import { useToastStore } from '@/stores/toastStore'
import NotificationItem from '@/components/notification/NotificationItem.vue'

defineProps<{ title: string }>()
const emit = defineEmits<{ toggleSidebar: []; navigate: [] }>()

const auth = useAuthStore()
const notifications = useNotificationStore()
const toast = useToastStore()
const router = useRouter()

const menuOpen = ref(false)
const bellOpen = ref(false)
const menuRef = ref<HTMLElement | null>(null)
const bellRef = ref<HTMLElement | null>(null)

const greeting = computed(() => (auth.isAdmin ? 'Administrator' : auth.user?.firstName ?? 'there'))

function onDocumentClick(event: MouseEvent): void {
  const target = event.target as Node
  if (menuOpen.value && menuRef.value && !menuRef.value.contains(target)) menuOpen.value = false
  if (bellOpen.value && bellRef.value && !bellRef.value.contains(target)) bellOpen.value = false
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key !== 'Escape') return
  menuOpen.value = false
  bellOpen.value = false
}

onMounted(() => {
  document.addEventListener('click', onDocumentClick)
  document.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', onDocumentClick)
  document.removeEventListener('keydown', onKeydown)
})

function openBell(): void {
  bellOpen.value = !bellOpen.value
  menuOpen.value = false
  if (bellOpen.value) void notifications.fetchUnread(6)
}

async function openNotification(notification: import('@/types').Notification): Promise<void> {
  bellOpen.value = false
  if (!notification.read) {
    try {
      await notifications.markRead(notification.id)
    } catch (error) {
      toast.fromError(error, 'Unable to update the notification')
    }
  }
  if (notification.transactionId) {
    await router.push({ name: 'transaction-detail', params: { id: notification.transactionId } })
  }
}

async function markAllRead(): Promise<void> {
  try {
    const updated = await notifications.markAllRead()
    toast.success('Notifications cleared', `${updated} notification${updated === 1 ? '' : 's'} marked as read.`)
  } catch (error) {
    toast.fromError(error, 'Unable to update notifications')
  }
}

async function signOut(): Promise<void> {
  await auth.logout()
  notifications.reset()
  await router.push({ name: 'login' })
}

function goToSettings(): void {
  menuOpen.value = false
  emit('navigate')
  void router.push(auth.isAdmin ? { name: 'admin-settings' } : { name: 'settings' })
}
</script>

<template>
  <header
    class="sticky top-0 z-30 flex h-16 shrink-0 items-center gap-2 border-b border-border bg-surface/95 px-4 backdrop-blur sm:px-6"
  >
    <button
      type="button"
      class="-ml-1 flex h-10 w-10 items-center justify-center rounded-md text-ink-muted transition-colors hover:bg-surface-sunken hover:text-ink lg:hidden"
      aria-label="Open navigation menu"
      @click="emit('toggleSidebar')"
    >
      <Menu :size="20" aria-hidden="true" />
    </button>

    <div class="min-w-0 flex-1">
      <h2 class="truncate text-headline text-ink">{{ title }}</h2>
      <p class="hidden text-caption text-ink-subtle sm:block">
        {{ greeting }}<span v-if="auth.isAdmin"> · Finova Administration</span>
      </p>
    </div>

    <div class="hidden max-w-xs flex-1 items-center md:flex lg:max-w-sm">
      <div class="relative w-full">
        <label for="global-search" class="sr-only">Search transactions</label>
        <Search
          :size="15"
          class="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-ink-subtle"
          aria-hidden="true"
        />
        <input
          id="global-search"
          type="search"
          class="fin-input h-9 pl-9 text-[0.875rem]"
          placeholder="Search transactions, accounts…"
          autocomplete="off"
          @keydown.enter="router.push({ name: 'transactions', query: { q: ($event.target as HTMLInputElement).value } })"
        />
      </div>
    </div>

    <div ref="bellRef" class="relative">
      <button
        type="button"
        class="relative flex h-10 w-10 items-center justify-center rounded-md text-ink-muted transition-colors hover:bg-surface-sunken hover:text-ink"
        aria-label="Notifications"
        :aria-expanded="bellOpen"
        aria-haspopup="dialog"
        @click.stop="openBell"
      >
        <Bell :size="19" aria-hidden="true" />
        <span
          v-if="notifications.unreadCount > 0"
          class="absolute right-1 top-1 flex h-4 min-w-[16px] items-center justify-center rounded-full bg-danger px-1 text-[0.5625rem] font-bold text-white ring-2 ring-surface"
          aria-hidden="true"
        >
          {{ notifications.unreadCount > 9 ? '9+' : notifications.unreadCount }}
        </span>
      </button>

      <div
        v-if="bellOpen"
        class="absolute right-0 top-12 z-40 w-[min(23rem,calc(100vw-2rem))] overflow-hidden rounded-lg border border-border bg-surface shadow-overlay animate-scale-in"
        role="dialog"
        aria-label="Notifications"
      >
        <header class="flex items-center justify-between gap-2 border-b border-border px-4 py-3">
          <h3 class="text-[0.9375rem] font-semibold text-ink">Notifications</h3>
          <button
            v-if="notifications.unreadCount > 0"
            type="button"
            class="inline-flex items-center gap-1.5 text-caption font-semibold text-primary hover:underline"
            @click="markAllRead"
          >
            <CheckCheck :size="13" aria-hidden="true" />
            Mark all read
          </button>
        </header>

        <div class="fin-scroll-thin max-h-[22rem] overflow-y-auto p-2">
          <p v-if="notifications.unread.length === 0" class="px-2 py-8 text-center text-[0.875rem] text-ink-subtle">
            You are all caught up.
          </p>
          <NotificationItem
            v-for="notification in notifications.unread"
            :key="notification.id"
            :notification="notification"
            @click="openNotification"
            @mark-read="notifications.markRead(notification.id)"
          />
        </div>

        <footer class="border-t border-border bg-surface-sunken px-4 py-2.5">
          <RouterLink
            to="/notifications"
            class="text-caption font-semibold text-primary hover:underline"
            @click="bellOpen = false"
          >
            View all notifications
          </RouterLink>
        </footer>
      </div>
    </div>

    <div ref="menuRef" class="relative">
      <button
        type="button"
        class="flex items-center gap-2 rounded-md py-1 pl-1 pr-2 transition-colors hover:bg-surface-sunken"
        :aria-expanded="menuOpen"
        aria-haspopup="menu"
        aria-label="Account menu"
        @click.stop="menuOpen = !menuOpen"
      >
        <span
          class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary text-[0.6875rem] font-bold text-white"
          aria-hidden="true"
        >
          {{ auth.initials }}
        </span>
        <span class="hidden min-w-0 text-left sm:block">
          <span class="block max-w-[8rem] truncate text-[0.8125rem] font-semibold leading-tight text-ink">
            {{ auth.fullName }}
          </span>
          <span class="block text-[0.6875rem] leading-tight text-ink-subtle">
            {{ auth.isAdmin ? 'Administrator' : 'Customer' }}
          </span>
        </span>
      </button>

      <div
        v-if="menuOpen"
        class="absolute right-0 top-12 z-40 w-60 overflow-hidden rounded-lg border border-border bg-surface py-1 shadow-overlay animate-scale-in"
        role="menu"
      >
        <div class="border-b border-border px-4 py-3">
          <p class="truncate text-[0.875rem] font-semibold text-ink">{{ auth.fullName }}</p>
          <p class="mt-0.5 truncate text-caption text-ink-subtle">{{ auth.user?.email }}</p>
        </div>

        <RouterLink
          to="/security"
          class="flex items-center gap-2.5 px-4 py-2.5 text-[0.875rem] text-ink-muted transition-colors hover:bg-surface-sunken hover:text-ink"
          role="menuitem"
          @click="menuOpen = false"
        >
          <ShieldCheck :size="16" aria-hidden="true" />
          Security centre
        </RouterLink>

        <button
          type="button"
          class="flex w-full items-center gap-2.5 px-4 py-2.5 text-left text-[0.875rem] text-ink-muted transition-colors hover:bg-surface-sunken hover:text-ink"
          role="menuitem"
          @click="goToSettings"
        >
          <UserCog :size="16" aria-hidden="true" />
          Settings
        </button>

        <button
          type="button"
          class="flex w-full items-center gap-2.5 border-t border-border px-4 py-2.5 text-left text-[0.875rem] text-danger transition-colors hover:bg-danger-light"
          role="menuitem"
          @click="signOut"
        >
          <LogOut :size="16" aria-hidden="true" />
          Sign out
        </button>
      </div>
    </div>
  </header>
</template>