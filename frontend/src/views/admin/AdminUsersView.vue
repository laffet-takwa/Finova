<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowUpRight, Ban, CheckCircle2, ShieldAlert, UserRound, Users } from 'lucide-vue-next'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseInput from '@/components/ui/BaseInput.vue'
import BaseModal from '@/components/ui/BaseModal.vue'
import BaseSelect from '@/components/ui/BaseSelect.vue'
import AuditEntryRow from '@/components/ui/AuditEntryRow.vue'
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue'
import DetailRow from '@/components/ui/DetailRow.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import FilterBar from '@/components/ui/FilterBar.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import Pagination from '@/components/ui/Pagination.vue'
import SearchInput from '@/components/ui/SearchInput.vue'
import Skeleton from '@/components/ui/Skeleton.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import { useAdminStore } from '@/stores/adminStore'
import { useToastStore } from '@/stores/toastStore'
import { userApi } from '@/api'
import { formatDateTime, formatRelative, fullName, initials } from '@/utils/format'
import type { AdminUserFilters } from '@/api/accountApi'
import type { AuditLogEntry, Role, UserStatus, UserSummary } from '@/types'

const STATUS_OPTIONS = [
  { value: 'ACTIVE', label: 'Active' },
  { value: 'BLOCKED', label: 'Blocked' },
]

const ROLE_OPTIONS = [
  { value: 'CUSTOMER', label: 'Customer' },
  { value: 'ADMIN', label: 'Administrator' },
]

const TYPING_PHRASE = 'BLOCK'

const router = useRouter()
const adminStore = useAdminStore()
const toast = useToastStore()

const searchText = ref('')
const filters = reactive({
  search: '',
  status: '' as UserStatus | '',
  role: '' as Role | '',
})
const reason = ref('')

const page = ref(0)
const pageSize = ref(20)
const listError = ref<string | null>(null)

const selectedUser = ref<UserSummary | null>(null)
const detailOpen = ref(false)
const auditTrail = ref<AuditLogEntry[]>([])
const auditLoading = ref(false)
const auditError = ref<string | null>(null)

const confirmOpen = ref(false)
const confirmAction = ref<'block' | 'unblock'>('block')

const users = computed(() => adminStore.users?.content ?? [])
const totalElements = computed(() => adminStore.users?.totalElements ?? 0)
const totalPages = computed(() => adminStore.users?.totalPages ?? 0)
const loading = computed(() => adminStore.loading)
const acting = computed(() => adminStore.acting)

const activeFilterCount = computed(
  () => [filters.search, filters.status, filters.role].filter((value) => String(value).trim().length > 0).length,
)

const blockingUser = computed(() =>
  confirmAction.value === 'block' ? selectedUser.value : null,
)

function buildQuery(targetPage = page.value): AdminUserFilters {
  return {
    search: filters.search || undefined,
    status: filters.status || undefined,
    role: filters.role || undefined,
    page: targetPage,
    size: pageSize.value,
    sort: 'createdAt,desc',
  }
}

async function load(): Promise<void> {
  listError.value = null
  try {
    await adminStore.fetchUsers(buildQuery(), true)
  } catch (cause) {
    listError.value = cause instanceof Error ? cause.message : 'We could not load the user directory.'
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
  filters.status = ''
  filters.role = ''
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

/** Row click: the detail modal also loads this user's own audit trail, which is what an operator needs. */
async function openUser(user: UserSummary): Promise<void> {
  selectedUser.value = user
  detailOpen.value = true
  auditTrail.value = []
  auditError.value = null
  auditLoading.value = true
  try {
    const pageOfEntries = await userApi.auditLogs({ userId: user.id, page: 0, size: 8 })
    auditTrail.value = pageOfEntries.content
  } catch (cause) {
    auditError.value =
      cause instanceof Error ? cause.message : 'We could not load this user’s activity.'
  } finally {
    auditLoading.value = false
  }
}

function askBlock(user: UserSummary): void {
  selectedUser.value = user
  confirmAction.value = 'block'
  reason.value = ''
  confirmOpen.value = true
}

function askUnblock(user: UserSummary): void {
  selectedUser.value = user
  confirmAction.value = 'unblock'
  reason.value = ''
  confirmOpen.value = true
}

async function applyStatus(): Promise<void> {
  const user = selectedUser.value
  if (!user || acting.value) return

  const status: UserStatus = confirmAction.value === 'block' ? 'BLOCKED' : 'ACTIVE'
  try {
    await adminStore.setUserStatus(user.id, status, reason.value.trim() || undefined)
    confirmOpen.value = false
    if (selectedUser.value?.id === user.id) {
      selectedUser.value = { ...selectedUser.value, status }
    }
    toast.success(
      status === 'BLOCKED' ? 'User blocked' : 'User unblocked',
      status === 'BLOCKED'
        ? `${fullName(user)} can no longer sign in. Their accounts keep their balances but cannot move money.`
        : `${fullName(user)} can sign in and use Finova again.`,
    )
  } catch (cause) {
    toast.fromError(
      cause,
      status === 'BLOCKED' ? 'We could not block this user' : 'We could not unblock this user',
    )
  }
}

function goToAccounts(userId: string): void {
  void router.push({ name: 'admin-accounts', query: { user: userId } })
}

watch(() => [filters.status, filters.role], () => reload())

onMounted(load)
</script>

<template>
  <div class="space-y-5 pb-4">
    <PageHeader
      eyebrow="Finova Administration"
      title="Users"
      description="Every customer and administrator registered on the platform, with the access each one currently has."
    >
      <template #actions>
        <p class="text-caption text-ink-subtle">
          <span aria-live="polite" class="font-semibold text-ink">
            {{ totalElements.toLocaleString('en-US') }}
          </span>
          user{{ totalElements === 1 ? '' : 's' }} on file
        </p>
      </template>
    </PageHeader>

    <div v-if="loading && users.length === 0 && !listError" class="fin-card p-5" aria-busy="true">
      <Skeleton variant="block" :rows="4" />
      <span class="sr-only">Loading the user directory</span>
    </div>

    <ErrorState
      v-else-if="listError && users.length === 0"
      class="fin-card"
      title="We could not load the user directory"
      :description="listError"
      retry-label="Reload users"
      @retry="load"
    />

    <template v-else>
      <FilterBar :active-count="activeFilterCount" @reset="onReset">
        <template #search>
          <SearchInput
            v-model="searchText"
            label="users"
            placeholder="Search by name or email address"
            :debounce-ms="350"
            @search="onSearch"
          />
        </template>

        <template #primary>
          <BaseSelect
            v-model="filters.status"
            label="Status"
            :options="STATUS_OPTIONS"
            placeholder="All statuses"
            class="w-full sm:w-40"
          />
        </template>

        <template #advanced>
          <BaseSelect
            v-model="filters.role"
            label="Role"
            :options="ROLE_OPTIONS"
            placeholder="All roles"
          />
        </template>
      </FilterBar>

      <div
        v-if="listError"
        class="rounded-md bg-danger-light px-3 py-2.5 text-[0.875rem] text-danger-dark"
        role="alert"
      >
        {{ listError }}
        <button type="button" class="ml-2 font-semibold underline" @click="load">Try again</button>
      </div>

      <h2 class="sr-only">User directory</h2>

      <div v-if="loading" class="sr-only" role="status" aria-live="polite">Refreshing the directory</div>

      <!-- Desktop -->
      <div v-if="users.length" class="fin-card hidden overflow-hidden lg:block">
        <div class="fin-scroll-thin overflow-x-auto">
          <table class="w-full min-w-[62rem] border-collapse">
            <caption class="sr-only">Registered Finova users</caption>
            <thead>
              <tr>
                <th scope="col" class="fin-table-header">User</th>
                <th scope="col" class="fin-table-header">Phone</th>
                <th scope="col" class="fin-table-header">Role</th>
                <th scope="col" class="fin-table-header">Status</th>
                <th scope="col" class="fin-table-header">Joined</th>
                <th scope="col" class="fin-table-header">Last login</th>
                <th scope="col" class="fin-table-header text-right">
                  <span class="sr-only">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="user in users"
                :key="user.id"
                class="cursor-pointer transition-colors hover:bg-surface-sunken"
                tabindex="0"
                @click="openUser(user)"
                @keydown.enter="openUser(user)"
                @keydown.space.prevent="openUser(user)"
              >
                <td class="fin-table-cell">
                  <span class="flex items-center gap-3">
                    <span
                      class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-primary-soft text-caption font-bold text-primary"
                      aria-hidden="true"
                    >
                      {{ initials(user.firstName, user.lastName) }}
                    </span>
                    <span class="min-w-0">
                      <span class="block truncate text-[0.9375rem] font-medium text-ink">
                        {{ fullName(user) }}
                      </span>
                      <span class="mt-0.5 block truncate text-caption text-ink-subtle">
                        {{ user.email }}
                      </span>
                    </span>
                  </span>
                </td>
                <td class="fin-table-cell whitespace-nowrap text-[0.9375rem] text-ink-muted">
                  {{ user.phone || '—' }}
                </td>
                <td class="fin-table-cell">
                  <span
                    class="fin-chip"
                    :class="user.role === 'ADMIN' ? 'bg-accent-light text-accent-dark' : 'bg-surface-sunken text-ink-muted'"
                  >
                    {{ user.role === 'ADMIN' ? 'Administrator' : 'Customer' }}
                  </span>
                </td>
                <td class="fin-table-cell">
                  <StatusBadge :status="user.status" />
                </td>
                <td class="fin-table-cell whitespace-nowrap text-[0.875rem] text-ink-muted">
                  {{ formatDateTime(user.createdAt) }}
                </td>
                <td class="fin-table-cell whitespace-nowrap text-[0.875rem] text-ink-muted">
                  {{ user.lastLoginAt ? formatRelative(user.lastLoginAt) : 'Never signed in' }}
                </td>
                <td class="fin-table-cell whitespace-nowrap text-right" @click.stop @keydown.stop>
                  <BaseButton
                    v-if="user.status === 'ACTIVE'"
                    variant="ghost"
                    size="sm"
                    :disabled="acting"
                    :aria-label="`Block ${fullName(user)}`"
                    @click="askBlock(user)"
                  >
                    <template #icon>
                      <Ban :size="14" aria-hidden="true" />
                    </template>
                    Block
                  </BaseButton>
                  <BaseButton
                    v-else
                    variant="ghost"
                    size="sm"
                    :disabled="acting"
                    :aria-label="`Unblock ${fullName(user)}`"
                    @click="askUnblock(user)"
                  >
                    <template #icon>
                      <CheckCircle2 :size="14" aria-hidden="true" />
                    </template>
                    Unblock
                  </BaseButton>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Mobile / tablet -->
      <ul v-if="users.length" class="fin-card divide-y divide-border lg:hidden">
        <li v-for="user in users" :key="user.id">
          <button
            type="button"
            class="flex w-full flex-col gap-2 p-4 text-left transition-colors hover:bg-surface-sunken"
            @click="openUser(user)"
          >
            <span class="flex items-start gap-3">
              <span
                class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-primary-soft text-caption font-bold text-primary"
                aria-hidden="true"
              >
                {{ initials(user.firstName, user.lastName) }}
              </span>
              <span class="min-w-0 flex-1">
                <span class="block truncate text-[0.9375rem] font-medium text-ink">
                  {{ fullName(user) }}
                </span>
                <span class="mt-0.5 block truncate text-caption text-ink-subtle">{{ user.email }}</span>
              </span>
              <StatusBadge :status="user.status" size="sm" />
            </span>

            <span class="text-caption text-ink-subtle">
              {{ user.role === 'ADMIN' ? 'Administrator' : 'Customer' }}
              <span aria-hidden="true">·</span> {{ user.phone || 'No phone on file' }}
            </span>

            <span class="text-caption text-ink-subtle">
              Joined {{ formatDateTime(user.createdAt) }} ·
              {{ user.lastLoginAt ? `last seen ${formatRelative(user.lastLoginAt)}` : 'never signed in' }}
            </span>
          </button>

          <div class="flex flex-wrap items-center justify-end gap-2 border-t border-border px-4 pb-4 pt-3">
            <BaseButton
              v-if="user.status === 'ACTIVE'"
              variant="secondary"
              size="sm"
              :disabled="acting"
              @click="askBlock(user)"
            >
              <template #icon>
                <Ban :size="14" aria-hidden="true" />
              </template>
              Block
            </BaseButton>
            <BaseButton
              v-else
              variant="secondary"
              size="sm"
              :disabled="acting"
              @click="askUnblock(user)"
            >
              <template #icon>
                <CheckCircle2 :size="14" aria-hidden="true" />
              </template>
              Unblock
            </BaseButton>
            <BaseButton variant="ghost" size="sm" @click="goToAccounts(user.id)">
              <template #icon>
                <ArrowUpRight :size="14" aria-hidden="true" />
              </template>
              Accounts
            </BaseButton>
          </div>
        </li>
      </ul>

      <div v-if="!users.length" class="fin-card">
        <EmptyState
          :icon="Users"
          title="No users match these filters"
          description="No registered user matches the current search, status or role. Reset the filters to see the whole directory."
          action-label="Reset filters"
          @action="onReset"
        />
      </div>

      <div v-if="users.length" class="fin-card px-4 py-3.5">
        <Pagination
          :page="page"
          :total-pages="totalPages"
          :total-elements="totalElements"
          :page-size="pageSize"
          label="users"
          @change="onPageChange"
          @size-change="onSizeChange"
        />
      </div>
    </template>

    <!-- User detail -->
    <BaseModal
      :open="detailOpen"
      :title="selectedUser ? fullName(selectedUser) : 'User'"
      :description="selectedUser?.email"
      size="lg"
      @close="detailOpen = false"
    >
      <div v-if="selectedUser">
        <div class="flex flex-wrap items-center gap-2">
          <StatusBadge :status="selectedUser.status" />
          <span
            class="fin-chip"
            :class="selectedUser.role === 'ADMIN' ? 'bg-accent-light text-accent-dark' : 'bg-surface-sunken text-ink-muted'"
          >
            {{ selectedUser.role === 'ADMIN' ? 'Administrator' : 'Customer' }}
          </span>
          <span class="font-mono text-caption text-ink-subtle">{{ selectedUser.id }}</span>
        </div>

        <dl class="mt-3 divide-y divide-border border-t border-border">
          <DetailRow label="Email" :value="selectedUser.email" />
          <DetailRow label="Phone" :value="selectedUser.phone ?? undefined" :hint="selectedUser.phone ? undefined : 'No phone number on file.'" />
          <DetailRow label="Registered" :value="formatDateTime(selectedUser.createdAt)" />
          <DetailRow
            label="Last login"
            :value="selectedUser.lastLoginAt ? formatDateTime(selectedUser.lastLoginAt) : undefined"
            :hint="selectedUser.lastLoginAt ? formatRelative(selectedUser.lastLoginAt) : 'This user has never signed in.'"
          />
        </dl>

        <h3 class="mt-5 flex items-center gap-2 text-headline text-ink">
          <ShieldAlert :size="16" class="text-primary" aria-hidden="true" />
          Recent activity
        </h3>
        <p class="mt-1 text-caption text-ink-muted">
          Audit entries this user produced. The full trail lives in the audit log.
        </p>

        <div v-if="auditLoading" class="mt-3" aria-busy="true">
          <Skeleton variant="block" :rows="2" />
          <span class="sr-only">Loading this user’s activity</span>
        </div>

        <ErrorState
          v-else-if="auditError"
          class="mt-3 rounded-md border border-border"
          compact
          title="We could not load this user’s activity"
          :description="auditError"
          retry-label="Try again"
          @retry="selectedUser && openUser(selectedUser)"
        />

        <EmptyState
          v-else-if="!auditTrail.length"
          class="mt-3 rounded-md border border-border"
          compact
          :icon="UserRound"
          title="No recorded activity"
          description="This user has not produced an audit entry yet. Entries appear here from their first sign-in."
        />

        <ul v-else class="mt-3 divide-y divide-border/70">
          <li v-for="entry in auditTrail" :key="entry.id">
            <AuditEntryRow :entry="entry" />
          </li>
        </ul>
      </div>

      <template #footer>
        <BaseButton
          v-if="selectedUser"
          variant="secondary"
          @click="selectedUser && goToAccounts(selectedUser.id)"
        >
          View this user’s accounts
        </BaseButton>
        <BaseButton
          v-if="selectedUser?.status === 'ACTIVE'"
          variant="danger"
          :disabled="acting"
          @click="askBlock(selectedUser)"
        >
          <template #icon>
            <Ban :size="15" aria-hidden="true" />
          </template>
          Block user
        </BaseButton>
        <BaseButton
          v-else-if="selectedUser"
          variant="primary"
          :disabled="acting"
          @click="askUnblock(selectedUser)"
        >
          <template #icon>
            <CheckCircle2 :size="15" aria-hidden="true" />
          </template>
          Unblock user
        </BaseButton>
      </template>
    </BaseModal>

    <ConfirmDialog
      :open="confirmOpen"
      :title="confirmAction === 'block' ? 'Block this user' : 'Unblock this user'"
      :message="
        confirmAction === 'block'
          ? `Block ${selectedUser ? fullName(selectedUser) : 'this user'}? They will be signed out and cannot sign in again until an administrator unblocks them.`
          : `Restore sign-in for ${selectedUser ? fullName(selectedUser) : 'this user'}?`
      "
      :detail="
        confirmAction === 'block'
          ? 'Blocking is immediate and affects every device. Their accounts keep their balances, but no transfer, deposit or withdrawal can be started while the account is blocked, and any transfer already flagged for fraud review stays held. Block the wrong person and a real customer is locked out of their own money until this is reversed.'
          : 'Unblocking restores sign-in. Accounts that were blocked separately are not reactivated by this action — open the account and activate it from the accounts screen.'
      "
      :confirm-label="confirmAction === 'block' ? 'Block user' : 'Unblock user'"
      :tone="confirmAction === 'block' ? 'danger' : 'primary'"
      :require-typing="confirmAction === 'block'"
      :typing-phrase="TYPING_PHRASE"
      :loading="acting"
      @confirm="applyStatus"
      @cancel="confirmOpen = false"
    >
      <BaseInput
        v-model="reason"
        label="Reason"
        type="text"
        :placeholder="confirmAction === 'block' ? 'Recorded in the audit trail' : 'Recorded in the audit trail'"
        :maxlength="180"
        :hint="blockingUser ? `Stored against ${fullName(blockingUser)} in the audit log.` : 'Stored in the audit log.'"
      />
    </ConfirmDialog>
  </div>
</template>