import { mockAccountApi, mockNotificationPreferences, mockUserApi } from './accountMock'
import { mockTransactionApi } from './transactionMock'
import { mockFraudApi } from './fraudMock'
import { mockNotificationApi } from './notificationMock'

/**
 * Admin aggregate API backed by the same in-memory fixtures as the customer
 * modules, so the console is populated the moment an admin signs in.
 */
export const mockAdminApi = {
  users: (filters: Parameters<typeof mockUserApi.adminList>[0]) => mockUserApi.adminList(filters),
  user: (id: string) => mockUserApi.adminGet(id),
  setUserStatus: (id: string, status: 'ACTIVE' | 'BLOCKED', reason?: string) =>
    mockUserApi.adminUpdateStatus(id, status).then((user) => ({ ...user, reason })),

  accounts: (filters: Parameters<typeof mockAccountApi.adminList>[0]) => mockAccountApi.adminList(filters),
  setAccountStatus: (
    id: string,
    status: 'ACTIVE' | 'BLOCKED' | 'CLOSED',
    reason?: string,
  ) => mockAccountApi.updateStatus(id, { status, reason }).then((account) => ({ ...account, reason })),

  transactions: (filters: Parameters<typeof mockTransactionApi.adminList>[0]) =>
    mockTransactionApi.adminList(filters),
  alerts: (filters: Parameters<typeof mockFraudApi.list>[0]) => mockFraudApi.list(filters),
  alert: (id: string) => mockFraudApi.get(id),
  markAlertSafe: (id: string, note?: string) => mockFraudApi.markSafe(id, { note }),
  confirmAlert: (id: string, note?: string) => mockFraudApi.confirmFraud(id, { note }),
  startAlertReview: (id: string, note?: string) => mockFraudApi.startReview(id, { note }),
  blockAlertAccount: (id: string, note?: string) => mockFraudApi.blockAccount(id, { note }),

  auditLogs: (filters: Parameters<typeof mockUserApi.auditLogs>[0]) => mockUserApi.auditLogs(filters),
  activityFeed: (filters: Parameters<typeof mockNotificationApi.adminFeed>[0]) =>
    mockNotificationApi.adminFeed(filters),
}

export { mockNotificationPreferences }