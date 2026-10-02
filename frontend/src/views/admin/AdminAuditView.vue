<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { FileSearch, ScrollText } from 'lucide-vue-next'
import BaseInput from '@/components/ui/BaseInput.vue'
import BaseSelect from '@/components/ui/BaseSelect.vue'
import CopyButton from '@/components/ui/CopyButton.vue'
import DateRangePicker from '@/components/ui/DateRangePicker.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import FilterBar from '@/components/ui/FilterBar.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import Pagination from '@/components/ui/Pagination.vue'
import SearchInput from '@/components/ui/SearchInput.vue'
import Skeleton from '@/components/ui/Skeleton.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import { adminApi } from '@/api'
import { formatDateTime, formatRelative } from '@/utils/format'
import type { AuditLogEntry, AuditResult, PageResponse } from '@/types'

/**
 * Mirrors `com.finova.common.audit.AuditAction` on the backend. Every one of
 * these is written by a service, so the list is the filter, not a guess.
 */
const ACTION_OPTIONS = [
  'LOGIN_SUCCESS',
  'LOGIN_FAILED',
  'LOGOUT',
  'TOKEN_REFRESHED',
  'USER_REGISTERED',
  'PROFILE_UPDATED',
  'ACCOUNT_CREATED',
  'ACCOUNT_BLOCKED',
  'ACCOUNT_STATUS_CHANGED',
  'TRANSFER_CREATED',
  'TRANSFER_COMPLETED',
  'TRANSFER_FAILED',
  'FRAUD_DETECTED',
  'FRAUD_REVIEWED',
  'NOTIFICATION_READ',
].map((value) => ({ value, label: value.replace(/_/g, ' ').toLowerCase() }))

const RESULT_OPTIONS = [
  { value: 'SUCCESS', label: 'Success' },
  { value: 'FAILURE', label: 'Failure' },
]

const searchText = ref('')
const filters = reactive({
  search: '',
  action: '',
  result: '' as AuditResult | '',
  userId: '',
  from: '',
  to: '',
})

const page = ref(0)
const pageSize = ref(25)
const entries = ref<AuditLogEntry[]>([])
const totalElements = ref(0)
const totalPages = ref(0)
const loading = ref(true)
const error = ref<string | null>(null)

const activeFilterCount = computed(
  () =>
    [filters.search, filters.action, filters.result, filters.userId, filters.from, filters.to].filter(
      (value) => String(value).trim().length > 0,
    ).length,
)

/** Transfer entries carry a TX reference, which is what an operator pastes elsewhere. */
function isTransferReference(entry: AuditLogEntry): boolean {
  return entry.resource.toLowerCase() === 'transaction' && Boolean(entry.resourceId)
}

function shortId(value: string | null | undefined): string {
  if (!value) return '—'
  return value.length <= 12 ? value : `${value.slice(0, 8)}…`
}

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const result: PageResponse<AuditLogEntry> = await adminApi.auditLogs({
      search: filters.search || undefined,
      action: filters.action || undefined,
      result: filters.result || undefined,
      userId: filters.userId || undefined,
      from: filters.from ? new Date(`${filters.from}T00:00:00`).toISOString() : undefined,
      to: filters.to ? new Date(`${filters.to}T23:59:59`).toISOString() : undefined,
      page: page.value,
      size: pageSize.value,
    })
    entries.value = result.content
    totalElements.value = result.totalElements
    totalPages.value = result.totalPages
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'We could not load the audit trail.'
  } finally {
    loading.value = false
  }
}

function reload(): void {
  page.value = 0
  void load()
}

function onSearch(value: string): void {
  if (filters.search === value) return
  filters.search = value
  reload()
}

function onReset(): void {
  searchText.value = ''
  filters.search = ''
  filters.action = ''
  filters.result = ''
  filters.userId = ''
  filters.from = ''
  filters.to = ''
  reload()
}

function onPageChange(next: number): void {
  page.value = next
  void load()
}

function onSizeChange(next: number): void {
  pageSize.value = next
  page.value = 0
  void load()
}

watch(() => [filters.action, filters.result, filters.userId], () => reload())
watch(() => [filters.from, filters.to], () => reload())

onMounted(load)
</script>

<template>
  <div class="space-y-5 pb-4">
    <PageHeader
      eyebrow="Finova Administration"
      title="Audit logs"
      description="An append-only record written by every Finova service as it handles a request. Entries are never edited or deleted, which is what makes the trail usable in a dispute."
    >
      <template #actions>
        <p class="text-caption text-ink-subtle">
          <span aria-live="polite" class="font-semibold text-ink">
            {{ totalElements.toLocaleString('en-US') }}
          </span>
          entr{{ totalElements === 1 ? 'y' : 'ies' }} matching
        </p>
      </template>
    </PageHeader>

    <p class="flex items-start gap-2 rounded-md bg-primary-soft px-3 py-2.5 text-caption text-ink-muted">
      <ScrollText :size="14" class="mt-0.5 shrink-0 text-primary" aria-hidden="true" />
      <span>
        Filter by action, result, user or window. The correlation id on each row is what ties one
        request together across the gateway and every service it touched — copy it into a support
        request to have a specific call traced.
      </span>
    </p>

    <div v-if="loading && entries.length === 0 && !error" class="fin-card p-5" aria-busy="true">
      <Skeleton variant="block" :rows="5" />
      <span class="sr-only">Loading the audit trail</span>
    </div>

    <ErrorState
      v-else-if="error && entries.length === 0"
      class="fin-card"
      title="We could not load the audit trail"
      :description="error"
      retry-label="Reload audit entries"
      @retry="load"
    />

    <template v-else>
      <FilterBar :active-count="activeFilterCount" @reset="onReset">
        <template #search>
          <SearchInput
            v-model="searchText"
            label="audit entries"
            placeholder="Search action or resource id"
            :debounce-ms="400"
            @search="onSearch"
          />
        </template>

        <template #primary>
          <BaseSelect
            v-model="filters.action"
            label="Action"
            :options="ACTION_OPTIONS"
            placeholder="All actions"
            class="w-full sm:w-52"
          />
        </template>

        <template #advanced>
          <BaseSelect
            v-model="filters.result"
            label="Result"
            :options="RESULT_OPTIONS"
            placeholder="Success and failure"
          />
          <BaseInput
            v-model="filters.userId"
            label="User id"
            placeholder="e.g. 3f2a9c14-…"
            hint="Exact identifier of the user the entry belongs to."
          />
          <DateRangePicker v-model:from="filters.from" v-model:to="filters.to" label="Recorded between" />
        </template>
      </FilterBar>

      <div
        v-if="error"
        class="rounded-md bg-danger-light px-3 py-2.5 text-[0.875rem] text-danger-dark"
        role="alert"
      >
        {{ error }}
        <button type="button" class="ml-2 font-semibold underline" @click="load">Try again</button>
      </div>

      <div v-if="loading && entries.length" class="sr-only" role="status" aria-live="polite">
        Refreshing the audit trail
      </div>

      <!-- Desktop -->
      <div v-if="entries.length" class="fin-card hidden overflow-hidden lg:block">
        <div class="fin-scroll-thin overflow-x-auto">
          <table class="w-full min-w-[72rem] border-collapse">
            <caption class="sr-only">Append-only Finova audit trail</caption>
            <thead>
              <tr>
                <th scope="col" class="fin-table-header">Recorded</th>
                <th scope="col" class="fin-table-header">Action</th>
                <th scope="col" class="fin-table-header">User</th>
                <th scope="col" class="fin-table-header">Resource</th>
                <th scope="col" class="fin-table-header">Service</th>
                <th scope="col" class="fin-table-header">IP address</th>
                <th scope="col" class="fin-table-header">Correlation id</th>
                <th scope="col" class="fin-table-header">Result</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="entry in entries" :key="entry.id" class="transition-colors hover:bg-surface-sunken">
                <td class="fin-table-cell whitespace-nowrap">
                  <span class="block text-[0.875rem] text-ink">{{ formatDateTime(entry.createdAt) }}</span>
                  <span class="mt-0.5 block text-caption text-ink-subtle">
                    {{ formatRelative(entry.createdAt) }}
                  </span>
                </td>
                <td class="fin-table-cell">
                  <span class="fin-chip border border-border bg-surface-sunken font-mono text-ink-muted">
                    {{ entry.action }}
                  </span>
                </td>
                <td class="fin-table-cell font-mono text-[0.8125rem] text-ink-muted">
                  {{ shortId(entry.userId) }}
                </td>
                <td class="fin-table-cell">
                  <span class="block text-[0.875rem] capitalize text-ink">{{ entry.resource }}</span>
                  <RouterLink
                    v-if="isTransferReference(entry)"
                    :to="{ name: 'admin-transactions', query: { q: entry.resourceId } }"
                    class="mt-0.5 block font-mono text-caption text-accent underline underline-offset-2 hover:text-accent-dark"
                  >
                    {{ entry.resourceId }}
                  </RouterLink>
                  <span v-else-if="entry.resourceId" class="mt-0.5 block truncate font-mono text-caption text-ink-subtle">
                    {{ entry.resourceId }}
                  </span>
                </td>
                <td class="fin-table-cell whitespace-nowrap text-[0.875rem] text-ink-muted">
                  {{ entry.service }}
                </td>
                <td class="fin-table-cell whitespace-nowrap font-mono text-[0.8125rem] text-ink-muted">
                  {{ entry.ipAddress || '—' }}
                </td>
                <td class="fin-table-cell whitespace-nowrap">
                  <span v-if="entry.correlationId" class="flex items-center gap-2">
                    <span class="font-mono text-[0.8125rem] text-ink-muted">{{ entry.correlationId }}</span>
                    <CopyButton :value="entry.correlationId" :label="`correlation id ${entry.correlationId}`" size="sm" />
                  </span>
                  <span v-else class="text-caption text-ink-subtle">—</span>
                </td>
                <td class="fin-table-cell">
                  <StatusBadge :status="entry.result" size="sm" />
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Mobile / tablet -->
      <ul v-if="entries.length" class="fin-card divide-y divide-border lg:hidden">
        <li v-for="entry in entries" :key="entry.id" class="flex flex-col gap-2 p-4">
          <div class="flex flex-wrap items-center gap-2">
            <span class="fin-chip border border-border bg-surface-sunken font-mono text-ink-muted">
              {{ entry.action }}
            </span>
            <StatusBadge :status="entry.result" size="sm" />
          </div>

          <p class="text-caption text-ink-subtle">
            <time :datetime="entry.createdAt">{{ formatDateTime(entry.createdAt) }}</time>
            · {{ entry.service }}
          </p>

          <p class="text-[0.875rem] text-ink">
            <span class="capitalize">{{ entry.resource }}</span>
            <span class="font-mono text-caption text-ink-subtle">
              {{ entry.resourceId ? ` ${entry.resourceId}` : '' }}
            </span>
          </p>

          <p v-if="isTransferReference(entry)" class="text-caption">
            <RouterLink
              :to="{ name: 'admin-transactions', query: { q: entry.resourceId } }"
              class="font-semibold text-accent underline underline-offset-2"
            >
              Open this transaction in the admin register
            </RouterLink>
          </p>

          <p class="font-mono text-caption text-ink-subtle">
            user {{ shortId(entry.userId) }} · ip {{ entry.ipAddress || '—' }}
          </p>

          <div v-if="entry.correlationId" class="flex flex-wrap items-center gap-2 border-t border-border pt-2.5">
            <span class="font-mono text-caption text-ink-muted">{{ entry.correlationId }}</span>
            <CopyButton :value="entry.correlationId" :label="`correlation id ${entry.correlationId}`" size="sm" />
          </div>
        </li>
      </ul>

      <div v-if="!entries.length" class="fin-card">
        <EmptyState
          :icon="FileSearch"
          title="No audit entries match these filters"
          description="Nothing in the trail matches the current action, result, user, search term or date window. Widen the date range first — audit entries age out of the read window."
          action-label="Reset filters"
          @action="onReset"
        />
      </div>

      <div v-if="entries.length" class="fin-card px-4 py-3.5">
        <Pagination
          :page="page"
          :total-pages="totalPages"
          :total-elements="totalElements"
          :page-size="pageSize"
          label="audit entries"
          @change="onPageChange"
          @size-change="onSizeChange"
        />
      </div>
    </template>
  </div>
</template>