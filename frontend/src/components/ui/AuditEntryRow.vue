<script setup lang="ts">
import { computed } from 'vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import { formatDateTime } from '@/utils/format'
import type { AuditLogEntry } from '@/types'

/**
 * One audit entry, rendered as a list row. Used wherever a compact trail is shown
 * outside a full table (the overview strip, the user detail dialog) so the
 * action / actor / result / timestamp reading is identical everywhere.
 */
const props = withDefaults(
  defineProps<{
    entry: AuditLogEntry
    /** Show the owning user id — useful on the platform-wide trail. */
    showUser?: boolean
    /** Show resource + resource id instead of service + IP address. */
    showResource?: boolean
  }>(),
  { showUser: false, showResource: false },
)

/** `ACCOUNT_STATUS_CHANGED` → `Account status changed`. */
const actionLabel = computed(() => {
  const words = props.entry.action.replace(/_/g, ' ').toLowerCase().trim()
  return words.charAt(0).toUpperCase() + words.slice(1)
})

const meta = computed(() => {
  const parts: string[] = []
  if (props.showResource) {
    parts.push(props.entry.resource)
    if (props.entry.resourceId) parts.push(props.entry.resourceId)
  } else {
    parts.push(props.entry.service)
    if (props.entry.ipAddress) parts.push(props.entry.ipAddress)
  }
  return parts.join(' · ')
})
</script>

<template>
  <div class="flex flex-col gap-1 py-3 sm:flex-row sm:items-center sm:justify-between sm:gap-4">
    <span class="min-w-0 flex-1">
      <span class="block text-[0.875rem] font-medium text-ink">{{ actionLabel }}</span>
      <span class="mt-0.5 block truncate text-caption text-ink-subtle">
        <template v-if="showUser && entry.userId">
          <span class="font-mono">{{ entry.userId }}</span>
          <span aria-hidden="true"> · </span>
        </template>
        {{ meta }}
      </span>
    </span>

    <span class="flex shrink-0 items-center gap-3">
      <StatusBadge :status="entry.result" size="sm" />
      <time :datetime="entry.createdAt" class="text-caption tabular-nums text-ink-subtle">
        {{ formatDateTime(entry.createdAt) }}
      </time>
    </span>
  </div>
</template>