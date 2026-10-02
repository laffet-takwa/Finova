import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
// Importing through `@/api` is what makes mock mode apply to the admin console:
// `@/api/adminApi` is the always-HTTP module, so a direct import would bypass it.
import { accountApi, adminApi, transactionApi, userApi } from '@/api'
import type { AdminUserFilters } from '@/api/adminApi'
import type {
  Account,
  AccountFilters,
  AccountStatsSummary,
  AdminSettings,
  AdminTransactionStats,
  AuditLogEntry,
  Notification,
  PageResponse,
  Transaction,
  TransactionFilters,
  UserStatsSummary,
  UserSummary,
} from '@/types'

const REFRESH_INTERVAL_MS = 120_000

export const useAdminStore = defineStore('admin', () => {
  const userStats = ref<UserStatsSummary | null>(null)
  const accountStats = ref<AccountStatsSummary | null>(null)
  const transactionStats = ref<AdminTransactionStats | null>(null)
  const openAlertCount = ref(0)
  const recentAudit = ref<AuditLogEntry[]>([])
  const recentActivity = ref<Notification[]>([])

  const users = ref<PageResponse<UserSummary> | null>(null)
  const accounts = ref<PageResponse<Account> | null>(null)
  const transactions = ref<PageResponse<Transaction> | null>(null)

  const loading = ref(false)
  const acting = ref(false)
  const loaded = ref(false)
  const error = ref<string | null>(null)

  const settings = ref<AdminSettings>({
    platformName: 'Finova',
    environment: 'development',
    maintenanceMode: false,
    transactionApprovalThreshold: 10000,
    maxTransfersPerHour: 20,
    sessionTimeoutMinutes: 60,
    fraudScoringEnabled: true,
    notificationsEnabled: true,
  })

  let pollTimer: number | null = null

  const totalUsers = computed(() => userStats.value?.totalUsers ?? 0)
  const activeAccounts = computed(() => accountStats.value?.activeAccounts ?? 0)
  const transactionsToday = computed(() => transactionStats.value?.completedToday ?? 0)
  const transactionVolume = computed(() => transactionStats.value?.volumeToday ?? 0)
  const fraudAlerts = computed(() => openAlertCount.value)

  const isReady = computed(
    () => userStats.value !== null || accountStats.value !== null || transactionStats.value !== null,
  )

  async function fetchOverview(force = false): Promise<void> {
    if (!force && loaded.value) return
    loading.value = true
    error.value = null

    const [
      usersResult,
      accountsResult,
      transactionStatsResult,
      transactionsResult,
      alertsResult,
      auditResult,
      activityResult,
    ] = await Promise.allSettled([
      userApi.adminStats(),
      accountApi.adminStats(),
      transactionApi.adminStats(),
      adminApi.transactions({ page: 0, size: 1 }),
      adminApi.alerts({ page: 0, size: 1, status: 'OPEN' }),
      adminApi.auditLogs({ page: 0, size: 8 }),
      adminApi.activityFeed({ page: 0, size: 8 }),
    ])

    if (usersResult.status === 'fulfilled') userStats.value = usersResult.value
    if (accountsResult.status === 'fulfilled') accountStats.value = accountsResult.value
    if (transactionStatsResult.status === 'fulfilled') transactionStats.value = transactionStatsResult.value
    if (transactionsResult.status === 'fulfilled') {
      const { content: _content, ...rest } = transactionsResult.value
      transactions.value = { content: [], ...rest }
    }
    if (alertsResult.status === 'fulfilled') openAlertCount.value = alertsResult.value.totalElements
    if (auditResult.status === 'fulfilled') recentAudit.value = auditResult.value.content
    if (activityResult.status === 'fulfilled') recentActivity.value = activityResult.value.content

    const allFailed =
      usersResult.status === 'rejected' &&
      accountsResult.status === 'rejected' &&
      transactionStatsResult.status === 'rejected' &&
      transactionsResult.status === 'rejected' &&
      alertsResult.status === 'rejected'
    if (allFailed) error.value = 'Unable to load the administration overview.'

    loaded.value = true
    loading.value = false
  }

  async function fetchUsers(filters: AdminUserFilters, force = false): Promise<void> {
    if (users.value && !force) return
    loading.value = true
    error.value = null
    try {
      users.value = await adminApi.users(filters)
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : 'Unable to load users.'
      throw cause
    } finally {
      loading.value = false
    }
  }

  async function fetchAccounts(filters: AccountFilters, force = false): Promise<void> {
    if (accounts.value && !force) return
    loading.value = true
    error.value = null
    try {
      accounts.value = await adminApi.accounts(filters)
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : 'Unable to load accounts.'
      throw cause
    } finally {
      loading.value = false
    }
  }

  async function fetchTransactions(filters: TransactionFilters, force = false): Promise<void> {
    if (transactions.value && !force) return
    loading.value = true
    error.value = null
    try {
      transactions.value = await adminApi.transactions(filters)
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : 'Unable to load transactions.'
      throw cause
    } finally {
      loading.value = false
    }
  }

  async function setUserStatus(id: string, status: 'ACTIVE' | 'BLOCKED', reason?: string): Promise<void> {
    acting.value = true
    try {
      await adminApi.setUserStatus(id, status, reason)
      if (users.value) {
        users.value = {
          ...users.value,
          content: users.value.content.map((user) => (user.id === id ? { ...user, status } : user)),
        }
      }
    } finally {
      acting.value = false
    }
  }

  async function setAccountStatus(
    id: string,
    status: 'ACTIVE' | 'BLOCKED' | 'CLOSED',
    reason?: string,
  ): Promise<void> {
    acting.value = true
    try {
      await adminApi.setAccountStatus(id, status, reason)
      if (accounts.value) {
        accounts.value = {
          ...accounts.value,
          content: accounts.value.content.map((account) =>
            account.id === id ? { ...account, status } : account,
          ),
        }
      }
    } finally {
      acting.value = false
    }
  }

  function startPolling(): void {
    if (pollTimer !== null) return
    pollTimer = window.setInterval(() => {
      void fetchOverview(true)
    }, REFRESH_INTERVAL_MS)
  }

  function stopPolling(): void {
    if (pollTimer === null) return
    window.clearInterval(pollTimer)
    pollTimer = null
  }

  function reset(): void {
    userStats.value = null
    accountStats.value = null
    transactionStats.value = null
    openAlertCount.value = 0
    recentAudit.value = []
    recentActivity.value = []
    users.value = null
    accounts.value = null
    transactions.value = null
    loaded.value = false
    error.value = null
    stopPolling()
  }

  return {
    userStats,
    accountStats,
    transactionStats,
    openAlertCount,
    recentAudit,
    recentActivity,
    users,
    accounts,
    transactions,
    loading,
    acting,
    loaded,
    error,
    settings,
    totalUsers,
    activeAccounts,
    transactionsToday,
    transactionVolume,
    fraudAlerts,
    isReady,
    fetchOverview,
    fetchUsers,
    fetchAccounts,
    fetchTransactions,
    setUserStatus,
    setAccountStatus,
    startPolling,
    stopPolling,
    reset,
  }
})
