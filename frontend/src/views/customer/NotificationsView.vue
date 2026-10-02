<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { BellOff, CheckCheck } from 'lucide-vue-next'
import PageHeader from '@/components/ui/PageHeader.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseToggle from '@/components/ui/BaseToggle.vue'
import SearchInput from '@/components/ui/SearchInput.vue'
import TabList, { type TabItem } from '@/components/ui/TabList.vue'
import Pagination from '@/components/ui/Pagination.vue'
import Skeleton from '@/components/ui/Skeleton.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import NotificationItem from '@/components/notification/NotificationItem.vue'
import ChartCard from '@/components/charts/ChartCard.vue'
import { useNotificationStore } from '@/stores/notificationStore'
import { useToastStore } from '@/stores/toastStore'
import { notificationApi } from '@/api'
import { AppError } from '@/utils/errors'
import type { Notification, NotificationCategory, NotificationStats } from '@/types'

const router = useRouter()
const notificationStore = useNotificationStore()
const toast = useToastStore()

const CATEGORY_LABELS: Record<NotificationCategory, string> = {
  TRANSACTIONS: 'Transactions',
  SECURITY: 'Security',
  SYSTEM: 'System',
}

const page = ref(0)
const pageSize = ref(20)
const category = ref<'' | NotificationCategory>('')
const unreadOnly = ref(false)
const search = ref('')
const searchText = ref('')
const markingAll = ref(false)
const stats = ref<NotificationStats | null>(null)
const statsLoading = ref(true)

const notifications = computed(() => notificationStore.notifications)
const loading = computed(() => notificationStore.loading)
const error = computed(() => notificationStore.error)
const offline = ref(false)

const tabs = computed<TabItem[]>(() => [
  { id: 'ALL', label: 'All', count: notificationStore.unreadCount || null },
  { id: 'TRANSACTIONS', label: 'Transactions', count: notificationStore.byCategory.TRANSACTIONS || null },
  { id: 'SECURITY', label: 'Security', count: notificationStore.byCategory.SECURITY || null },
  { id: 'SYSTEM', label: 'System', count: notificationStore.byCategory.SYSTEM || null },
])

const emptyTitle = computed(() => {
  if (search.value) return 'No notifications match your search'
  if (unreadOnly.value) return 'No unread notifications'
  if (category.value) return `No ${CATEGORY_LABELS[category.value].toLowerCase()} notifications yet`
  return "You're all caught up"
})

const emptyDescription = computed(() => {
  if (search.value) return 'Try a different word, or clear the search to see everything again.'
  if (unreadOnly.value) return 'Every notification in this view has been read. Switch off "Unread only" to review them again.'
  return 'Transfer updates, security alerts and system messages will appear here as soon as Finova sends them.'
})

const statsLabels = computed(() => stats.value?.last7Days.map((point) => point.label) ?? [])
const statsCounts = computed(() => stats.value?.last7Days.map((point) => point.count) ?? [])

async function load(): Promise<void> {
  offline.value = false
  try {
    await notificationStore.fetchList({
      page: page.value,
      size: pageSize.value,
      category: category.value || undefined,
      unreadOnly: unreadOnly.value || undefined,
      search: search.value || undefined,
    })
    offline.value = false
  } catch (cause) {
    offline.value = cause instanceof AppError ? cause.isNetworkError : false
  }
}

async function loadStats(): Promise<void> {
  statsLoading.value = true
  try {
    stats.value = await notificationApi.stats()
  } catch {
    // The chart is optional context; the inbox is the priority.
    stats.value = null
  } finally {
    statsLoading.value = false
  }
}

function reload(): void {
  page.value = 0
  void load()
}

function onSearch(value: string): void {
  if (search.value === value) return
  search.value = value
  reload()
}

function onTabChange(next: string): void {
  category.value = next === 'ALL' ? '' : (next as NotificationCategory)
  reload()
}

function onPageChange(next: number): void {
  page.value = next
  void load()
}

function onClearFilters(): void {
  search.value = ''
  searchText.value = ''
  unreadOnly.value = false
  category.value = ''
  reload()
}

function onSizeChange(next: number): void {
  pageSize.value = next
  page.value = 0
  void load()
}

async function onMarkRead(notification: Notification): Promise<void> {
  try {
    if (!notification.read) await notificationStore.markRead(notification.id)
  } catch (cause) {
    toast.fromError(cause, 'We could not mark that notification as read')
  }
}

async function onOpen(notification: Notification): Promise<void> {
  await onMarkRead(notification)
  if (notification.transactionId) {
    await router.push({ name: 'transaction-detail', params: { id: notification.transactionId } })
  }
}

async function onMarkAllRead(): Promise<void> {
  if (markingAll.value || notificationStore.unreadCount === 0) return
  markingAll.value = true
  try {
    const updated = await notificationStore.markAllRead()
    toast.success(
      'Everything marked as read',
      updated === 1 ? 'One notification was updated.' : `${updated} notifications were updated.`,
    )
    await Promise.all([load(), loadStats()])
  } catch (cause) {
    toast.fromError(cause, 'We could not mark your notifications as read')
  } finally {
    markingAll.value = false
  }
}

watch(unreadOnly, () => reload())

onMounted(async () => {
  await Promise.all([notificationStore.fetchUnreadCount(), load(), loadStats()])
})
</script>

<template>
  <div class="space-y-5 pb-4">
    <PageHeader
      eyebrow="Activity"
      title="Notifications"
      description="Transfer outcomes, security alerts and system messages from Finova."
    >
      <template #actions>
        <BaseButton
          variant="secondary"
          :loading="markingAll"
          :disabled="notificationStore.unreadCount === 0 || markingAll"
          @click="onMarkAllRead"
        >
          <template #icon>
            <CheckCheck :size="16" aria-hidden="true" />
          </template>
          Mark all as read
        </BaseButton>
      </template>
    </PageHeader>

    <div class="grid gap-5 xl:grid-cols-[minmax(0,1fr)_19rem] xl:items-start">
      <div class="min-w-0 space-y-4">
        <TabList
          :tabs="tabs"
          :model-value="category || 'ALL'"
          aria-label="Notification categories"
          @update:model-value="onTabChange"
        />

        <div class="fin-card p-4">
          <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div class="min-w-[12rem] flex-1">
              <SearchInput
                v-model="searchText"
                label="notifications"
                placeholder="Search notifications"
                debounce-ms="350"
                @search="onSearch"
              />
            </div>
            <div class="border-t border-border pt-1 sm:w-64 sm:border-l sm:border-t-0 sm:pl-4 sm:pt-0">
              <BaseToggle
                v-model="unreadOnly"
                label="Unread only"
                description="Hide notifications you have already read"
              />
            </div>
          </div>
        </div>

        <!-- Loading -->
        <div v-if="loading && notifications.length === 0" class="fin-card space-y-4 p-4" aria-busy="true">
          <Skeleton v-for="row in 4" :key="row" width="85%" />
          <span class="sr-only">Loading your notifications</span>
        </div>

        <!-- Error -->
        <ErrorState
          v-else-if="error && notifications.length === 0"
          class="fin-card"
          title="We could not load your notifications"
          :description="error"
          :offline="offline"
          retry-label="Reload notifications"
          @retry="load"
        />

        <!-- Empty -->
        <div v-else-if="notifications.length === 0" class="fin-card">
          <EmptyState
            :title="emptyTitle"
            :description="emptyDescription"
            :icon="BellOff"
            compact
          >
            <BaseButton
              v-if="search || unreadOnly || category"
              variant="secondary"
              size="sm"
              @click="onClearFilters"
            >
              Clear filters
            </BaseButton>
          </EmptyState>
        </div>

        <!-- List -->
        <template v-else>
          <ul class="fin-card divide-y divide-border" aria-label="Notifications">
            <li v-for="notification in notifications" :key="notification.id">
              <NotificationItem
                :notification="notification"
                :unread="!notification.read"
                @click="onOpen"
                @mark-read="onMarkRead"
              />
            </li>
          </ul>

          <div class="fin-card px-4 py-3.5">
            <Pagination
              :page="notificationStore.currentPage"
              :total-pages="notificationStore.totalPages"
              :total-elements="notificationStore.totalElements"
              :page-size="notificationStore.pageSize"
              label="notifications"
              @change="onPageChange"
              @size-change="onSizeChange"
            />
          </div>
        </template>
      </div>

      <!-- Context: last 7 days -->
      <aside class="space-y-4">
        <ChartCard
          type="bar"
          title="Last 7 days"
          :subtitle="stats ? `${stats.total} notifications in total · ${stats.unread} unread` : undefined"
          :labels="statsLabels"
          :datasets="[{ label: 'Notifications', data: statsCounts, color: '#3B82F6' }]"
          :currency="''"
          :height="190"
          :loading="statsLoading"
          empty-message="No notifications were sent in the last 7 days."
        />

        <section class="fin-card p-4">
          <h2 class="text-label text-ink">What lands here</h2>
          <ul class="mt-2 space-y-2 text-caption text-ink-muted">
            <li class="flex items-start gap-2">
              <span class="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-primary" aria-hidden="true" />
              Transfer receipts, failures and holds as soon as they happen.
            </li>
            <li class="flex items-start gap-2">
              <span class="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-warning" aria-hidden="true" />
              Security alerts, including new-device sign-ins and blocked accounts.
            </li>
            <li class="flex items-start gap-2">
              <span class="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-ink-subtle" aria-hidden="true" />
              System messages about maintenance and platform changes.
            </li>
          </ul>
          <p class="mt-3 text-caption text-ink-subtle">
            Choose which of these you receive in
            <RouterLink to="/settings" class="fin-link">notification settings</RouterLink>.
          </p>
        </section>
      </aside>
    </div>
  </div>
</template>
