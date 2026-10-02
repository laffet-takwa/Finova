<script setup lang="ts">
import { computed } from 'vue'
import {
  BellRing,
  CheckCircle2,
  CircleDollarSign,
  ShieldAlert,
  TriangleAlert,
  Wallet,
} from 'lucide-vue-next'
import type { Notification, NotificationSeverity } from '@/types'
import { formatAmount, formatRelative } from '@/utils/format'

const props = withDefaults(
  defineProps<{
    notification: Notification
    unread?: boolean
    onDark?: boolean
  }>(),
  { unread: undefined, onDark: false },
)

defineEmits<{ click: [notification: Notification]; markRead: [notification: Notification] }>()

const isUnread = computed(() => props.unread ?? !props.notification.read)

const config: Record<NotificationSeverity, { icon: typeof CheckCircle2; tone: string; ring: string }> = {
  SUCCESS: { icon: CheckCircle2, tone: 'text-success', ring: 'bg-success-light' },
  WARNING: { icon: TriangleAlert, tone: 'text-warning-dark', ring: 'bg-warning-light' },
  DANGER: { icon: ShieldAlert, tone: 'text-danger', ring: 'bg-danger-light' },
  INFO: { icon: BellRing, tone: 'text-accent', ring: 'bg-accent-light' },
}

const style = computed(() => config[props.notification.severity] ?? config.INFO)

const icon = computed(() => {
  if (props.notification.category === 'SECURITY') return ShieldAlert
  if (props.notification.type === 'TRANSFER_COMPLETED') return CircleDollarSign
  if (props.notification.type === 'TRANSFER_FLAGGED') return TriangleAlert
  if (props.notification.type === 'ACCOUNT_BLOCKED') return Wallet
  return BellRing
})

const amountSuffix = computed(() => {
  if (props.notification.amount === null || props.notification.amount === undefined) return ''
  return ` · ${formatAmount(props.notification.amount)} ${props.notification.currency ?? ''}`.trim()
})
</script>

<template>
  <div
    class="group relative flex w-full items-start gap-3 rounded-md px-2 py-2.5 text-left transition-colors"
    :class="[isUnread ? 'bg-primary-soft/50' : 'hover:bg-surface-sunken', onDark ? 'hover:bg-white/5' : '']"
  >
    <button
      type="button"
      class="flex min-w-0 flex-1 items-start gap-3 text-left"
      @click="$emit('click', notification)"
    >
      <span
        class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full"
        :class="style.ring"
        aria-hidden="true"
      >
        <component :is="icon" :size="17" :class="style.tone" />
      </span>

      <span class="min-w-0 flex-1">
        <span class="flex items-center gap-2">
          <span class="truncate text-[0.9375rem] font-semibold text-ink">
            {{ notification.title }}
          </span>
          <span
            v-if="isUnread"
            class="h-1.5 w-1.5 shrink-0 rounded-full bg-accent"
            :title="'Unread'"
            :aria-label="'Unread'"
          />
        </span>
        <span class="mt-0.5 block text-[0.8125rem] leading-relaxed text-ink-muted">
          {{ notification.message }}
        </span>
        <span class="mt-1 flex flex-wrap items-center gap-x-2 text-caption text-ink-subtle">
          <time :datetime="notification.createdAt">{{ formatRelative(notification.createdAt) }}</time>
          <span v-if="notification.reference" aria-hidden="true">·</span>
          <span v-if="notification.reference" class="font-mono">{{ notification.reference }}</span>
        </span>
      </span>
    </button>

    <button
      v-if="isUnread"
      type="button"
      class="mt-0.5 hidden h-7 shrink-0 items-center rounded px-2 text-caption font-semibold text-primary transition-colors hover:bg-primary-soft group-hover:flex"
      :aria-label="`Mark '${notification.title}' as read`"
      @click.stop="$emit('markRead', notification)"
    >
      Mark read
    </button>
  </div>
</template>