import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { notificationApi } from '@/api'
import type { Notification, NotificationPreference, PageResponse } from '@/types'

const POLL_INTERVAL_MS = 60_000

export const useNotificationStore = defineStore('notifications', () => {
  const notifications = ref<Notification[]>([])
  /** The full page envelope, so the notification centre can paginate and count. */
  const page = ref<PageResponse<Notification> | null>(null)
  const unread = ref<Notification[]>([])
  const unreadCount = ref(0)
  const byCategory = ref<Record<string, number>>({})
  const loading = ref(false)
  const loaded = ref(false)
  const error = ref<string | null>(null)
  const preferences = ref<NotificationPreference | null>(null)
  const panelOpen = ref(false)

  let pollTimer: number | null = null

  const hasUnread = computed(() => unreadCount.value > 0)
  const totalElements = computed(() => page.value?.totalElements ?? 0)
  const totalPages = computed(() => page.value?.totalPages ?? 0)
  const currentPage = computed(() => page.value?.page ?? 0)
  const pageSize = computed(() => page.value?.size ?? 20)

  async function fetchUnreadCount(): Promise<number> {
    try {
      const result = await notificationApi.unreadCount()
      unreadCount.value = result.unread
      byCategory.value = result.byCategory ?? {}
      return result.unread
    } catch {
      return unreadCount.value
    }
  }

  async function fetchUnread(limit = 20): Promise<Notification[]> {
    unread.value = await notificationApi.unread(limit)
    return unread.value
  }

  async function fetchList(filters: import('@/types').NotificationFilters = {}): Promise<void> {
    loading.value = true
    error.value = null
    try {
      const result = await notificationApi.list({ page: 0, size: 20, ...filters })
      page.value = result
      notifications.value = result.content
      loaded.value = true
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : 'Unable to load notifications.'
    } finally {
      loading.value = false
    }
  }

  async function markRead(id: string): Promise<void> {
    const updated = await notificationApi.markRead(id)
    const patch = (list: Notification[]) =>
      list.map((item) => (item.id === id ? { ...item, read: true, readAt: updated.readAt } : item))
    notifications.value = patch(notifications.value)
    if (page.value) page.value = { ...page.value, content: patch(page.value.content) }
    unread.value = patch(unread.value).filter((item) => !item.read)
    unreadCount.value = Math.max(0, unreadCount.value - 1)
  }

  async function markAllRead(): Promise<number> {
    const result = await notificationApi.markAllRead()
    const now = new Date().toISOString()
    notifications.value = notifications.value.map((item) => ({ ...item, read: true, readAt: item.readAt ?? now }))
    unread.value = []
    unreadCount.value = 0
    byCategory.value = {}
    return result.updated
  }

  async function fetchPreferences(): Promise<NotificationPreference | null> {
    try {
      preferences.value = await notificationApi.preferences()
    } catch {
      preferences.value = null
    }
    return preferences.value
  }

  async function updatePreferences(payload: NotificationPreference): Promise<NotificationPreference> {
    preferences.value = await notificationApi.updatePreferences(payload)
    return preferences.value
  }

  function startPolling(): void {
    if (pollTimer !== null) return
    void fetchUnreadCount()
    pollTimer = window.setInterval(() => {
      void fetchUnreadCount()
    }, POLL_INTERVAL_MS)
  }

  function stopPolling(): void {
    if (pollTimer === null) return
    window.clearInterval(pollTimer)
    pollTimer = null
  }

  function reset(): void {
    notifications.value = []
    page.value = null
    unread.value = []
    unreadCount.value = 0
    byCategory.value = {}
    loaded.value = false
    error.value = null
    preferences.value = null
    panelOpen.value = false
    stopPolling()
  }

  return {
    notifications,
    page,
    unread,
    unreadCount,
    byCategory,
    loading,
    loaded,
    error,
    preferences,
    panelOpen,
    hasUnread,
    totalElements,
    totalPages,
    currentPage,
    pageSize,
    fetchUnreadCount,
    fetchUnread,
    fetchList,
    markRead,
    markAllRead,
    fetchPreferences,
    updatePreferences,
    startPolling,
    stopPolling,
    reset,
  }
})
