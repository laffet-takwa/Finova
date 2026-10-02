import { accountApi, userApi, type AdminUserFilters } from '@/api/accountApi'
import { transactionApi } from '@/api/transactionApi'
import { fraudApi, type FraudFilters } from '@/api/fraudApi'
import { notificationApi } from '@/api/notificationApi'
import type {
  AccountFilters,
  AuditLogEntry,
  FraudAlert,
  Notification,
  PageResponse,
  TransactionFilters,
  UserSummary,
} from '@/types'

export type { AdminUserFilters } from '@/api/accountApi'

/**
 * Admin-only aggregate API. It is a thin composition of the resource APIs
 * because the admin console always reads across services; the gateway enforces
 * the ADMIN role on every underlying route.
 */
export const adminApi = {
  users: (filters: AdminUserFilters) => userApi.adminList(filters),
  user: (id: string) => userApi.adminGet(id),
  setUserStatus: (id: string, status: 'ACTIVE' | 'BLOCKED', reason?: string) =>
    userApi.adminUpdateStatus(id, status, reason),

  accounts: (filters: AccountFilters) => accountApi.adminList(filters),
  setAccountStatus: (id: string, status: 'ACTIVE' | 'BLOCKED' | 'CLOSED', reason?: string) =>
    accountApi.updateStatus(id, { status, reason }),

  transactions: (filters: TransactionFilters) => transactionApi.adminList(filters),
  alerts: (filters: FraudFilters) => fraudApi.list(filters),
  alert: (id: string) => fraudApi.get(id),
  markAlertSafe: (id: string, note?: string) => fraudApi.markSafe(id, { note }),
  confirmAlert: (id: string, note?: string) => fraudApi.confirmFraud(id, { note }),
  startAlertReview: (id: string, note?: string) => fraudApi.startReview(id, { note }),
  blockAlertAccount: (id: string, note?: string) => fraudApi.blockAccount(id, { note }),

  auditLogs: (filters: {
    userId?: string
    action?: string
    result?: string
    search?: string
    from?: string
    to?: string
    page?: number
    size?: number
  }) => userApi.auditLogs(filters),

  activityFeed: (filters: { userId?: string; page?: number; size?: number }) =>
    notificationApi.adminFeed(filters),
}

export interface AdminOverviewPayload {
  userStats: Awaited<ReturnType<typeof userApi.adminStats>>
  accountStats: Awaited<ReturnType<typeof accountApi.adminStats>>
  transactionStats: Awaited<ReturnType<typeof transactionApi.adminStats>>
  fraudStats: Awaited<ReturnType<typeof fraudApi.stats>>
  openAlerts: FraudAlert[]
  recentAudit: AuditLogEntry[]
  recentActivity: Notification[]
}

export type { PageResponse, UserSummary, TransactionFilters, AccountFilters, FraudFilters }
