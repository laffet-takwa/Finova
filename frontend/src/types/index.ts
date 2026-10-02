/**
 * Domain types mirroring the Finova backend contracts.
 * Money is always a number in major units here; the API speaks `numeric(19,3)`
 * strings which `apiClient` normalises on the way in.
 */

export type Role = 'CUSTOMER' | 'ADMIN'
export type UserStatus = 'ACTIVE' | 'BLOCKED'
export type AccountType = 'CHECKING' | 'SAVINGS'
export type Currency = 'TND' | 'EUR' | 'USD'
export type AccountStatus = 'ACTIVE' | 'BLOCKED' | 'CLOSED'
export type TransactionType = 'TRANSFER' | 'DEPOSIT' | 'WITHDRAWAL'
export type TransactionStatus =
  | 'PENDING'
  | 'PROCESSING'
  | 'COMPLETED'
  | 'FAILED'
  | 'REJECTED'
  | 'FLAGGED'
export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH'
export type FraudStatus = 'OPEN' | 'UNDER_REVIEW' | 'SAFE' | 'CONFIRMED'
export type NotificationType =
  | 'TRANSFER_COMPLETED'
  | 'TRANSFER_FAILED'
  | 'TRANSFER_FLAGGED'
  | 'ACCOUNT_BLOCKED'
  | 'SECURITY_ALERT'
export type NotificationCategory = 'TRANSACTIONS' | 'SECURITY' | 'SYSTEM'
export type NotificationSeverity = 'INFO' | 'SUCCESS' | 'WARNING' | 'DANGER'
export type AuditResult = 'SUCCESS' | 'FAILURE'

export interface ApiErrorBody {
  timestamp: string
  status: number
  code: string
  message: string
  path: string
  correlationId?: string
  details?: Record<string, string>
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface AuthUser {
  id: string
  firstName: string
  lastName: string
  email: string
  phone?: string | null
  role: Role
  status: UserStatus
  createdAt: string
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
  tokenType: string
  user: AuthUser
}

export interface RegisterRequest {
  firstName: string
  lastName: string
  email: string
  phone: string
  password: string
  confirmPassword: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface UpdateProfileRequest {
  firstName: string
  lastName: string
  phone: string
}

export interface UserSummary {
  id: string
  firstName: string
  lastName: string
  email: string
  phone?: string | null
  role: Role
  status: UserStatus
  createdAt: string
  lastLoginAt?: string | null
}

export interface UserStatsSummary {
  totalUsers: number
  activeUsers: number
  blockedUsers: number
  newUsersThisMonth: number
  growthSeries: SeriesPoint[]
}

export interface SeriesPoint {
  label: string
  count: number
  total?: number
}

export interface AuditLogEntry {
  id: string
  action: string
  userId?: string | null
  resource: string
  resourceId?: string | null
  ipAddress?: string | null
  correlationId?: string | null
  result: AuditResult
  service: string
  message?: string | null
  metadata?: Record<string, string> | null
  createdAt: string
}

export interface SecurityStatus {
  passwordProtected: boolean
  twoFactorEnabled: boolean
  mfaConfigured: boolean
  lastPasswordChange?: string | null
  locationSource: string
  recentLogins: SecurityLoginEntry[]
}

export interface SecurityLoginEntry {
  occurredAt: string
  ipAddress?: string | null
  device: string
  userAgent?: string | null
  location?: string | null
  result: AuditResult
}

export interface Account {
  id: string
  accountNumber: string
  maskedAccountNumber: string
  userId: string
  accountType: AccountType
  currency: Currency
  balance: number
  availableBalance: number
  status: AccountStatus
  nickname?: string | null
  iban?: string | null
  bankName?: string
  createdAt: string
  updatedAt: string
}

export interface AccountBalance {
  accountId: string
  accountNumber: string
  currency: Currency
  balance: number
  availableBalance: number
  status: AccountStatus
  asOf: string
}

export interface CreateAccountRequest {
  accountType: AccountType
  currency: Currency
  nickname?: string
  openingBalance?: number
}

export interface AccountStatusRequest {
  status: AccountStatus
  reason?: string
}

export interface BeneficiaryLookup {
  accountId: string
  userId: string
  accountNumber: string
  maskedAccountNumber: string
  accountType: AccountType
  currency: Currency
  holderDisplayName: string
  status: AccountStatus
  bankName: string
}

export interface AccountStatsSummary {
  totalAccounts: number
  activeAccounts: number
  blockedAccounts: number
  closedAccounts: number
  totalBalanceByCurrency: Record<string, number>
  accountsByType: Record<string, number>
  newAccountsLast30Days: number
  growthSeries: SeriesPoint[]
  dailyBalanceSeries: SeriesPoint[]
}

export interface AccountFilters {
  search?: string
  status?: AccountStatus
  accountType?: AccountType
  currency?: Currency
  userId?: string
  minBalance?: number
  maxBalance?: number
  from?: string
  to?: string
  page?: number
  size?: number
  sort?: string
}

export interface Transaction {
  id: string
  reference: string
  senderAccountId: string
  senderAccountNumber: string
  senderDisplay: string
  receiverAccountId: string
  receiverAccountNumber: string
  receiverDisplay: string
  amount: number
  currency: Currency
  fee: number
  totalAmount: number
  description?: string | null
  type: TransactionType
  status: TransactionStatus
  riskScore?: number | null
  riskLevel?: RiskLevel | null
  riskReasons?: string[] | null
  failureReason?: string | null
  createdAt: string
  completedAt?: string | null
  settledSenderBalance?: number | null
  settledReceiverBalance?: number | null
  correlationId?: string | null
}

export interface CreateTransactionRequest {
  senderAccountId: string
  receiverAccountNumber: string
  amount: number
  currency: Currency
  description?: string
}

export type TimelineState = 'DONE' | 'CURRENT' | 'PENDING' | 'FAILED'

export interface TimelineStep {
  key: string
  label: string
  description: string
  state: TimelineState
  at?: string | null
}

export interface TransactionTimeline {
  transactionId: string
  reference: string
  steps: TimelineStep[]
}

export interface TransactionFilters {
  search?: string
  type?: TransactionType
  status?: TransactionStatus
  accountId?: string
  currency?: Currency
  minAmount?: number
  maxAmount?: number
  from?: string
  to?: string
  userId?: string
  page?: number
  size?: number
  sort?: string
}

/**
 * Dashboard aggregates for the signed-in customer.
 *
 * There is deliberately no `currency`: a customer may hold TND, EUR and USD
 * accounts at once, and a single currency on an income/expense total would be
 * arithmetic that silently mixes them. The caller derives the display currency
 * from the accounts it already holds (`accountStore.primaryCurrency`).
 */
export interface TransactionSummary {
  income: number
  expenses: number
  transactionCount: number
  monthChangePercent: number
  monthChangeAbsolute: number
  dailySeries: Array<{ label: string; income: number; expenses: number; count: number }>
  recentTransactions: Transaction[]
}

export interface AdminTransactionStats {
  totalTransactions: number
  completedToday: number
  failedToday: number
  pendingCount: number
  flaggedCount: number
  successRate: number
  volumeToday: number
  volumeByCurrency: Record<string, number>
  statusDistribution: Record<string, number>
  typeDistribution: Record<string, number>
  hourlyVolume: SeriesPoint[]
  dailyVolume: SeriesPoint[]
  averageAmount: number
  largestAmount: number
}

export interface FraudTimelineStep {
  key: string
  label: string
  description: string
  at?: string | null
}

export interface FraudAlert {
  id: string
  transactionId: string
  reference: string
  senderAccountId: string
  receiverAccountId?: string | null
  senderAccountNumber: string
  senderUserId: string
  amount: number
  currency: Currency
  riskScore: number
  riskLevel: RiskLevel
  reasons: string[]
  triggeredRules: string[]
  status: FraudStatus
  createdAt: string
  updatedAt: string
  reviewedAt?: string | null
  reviewedBy?: string | null
  reviewNote?: string | null
  timeline: FraudTimelineStep[]
}

export interface FraudStatsSummary {
  openAlerts: number
  highRisk: number
  mediumRisk: number
  lowRisk: number
  resolvedToday: number
  confirmedToday: number
  totalAssessedToday: number
  averageRiskScore: number
  riskDistribution: Record<string, number>
  statusDistribution: Record<string, number>
  dailyAlerts: SeriesPoint[]
  topRiskyAccounts: Array<{ accountId: string; accountNumber: string; count: number; maxRiskScore: number }>
}

export interface FraudReviewRequest {
  note?: string
}

export interface Notification {
  id: string
  /**
   * Present only on the administrator activity feed (`/api/notifications/admin/feed`).
   * The customer inbox deliberately never carries it — the caller already knows
   * who they are, so it would be surface area for nothing.
   */
  userId?: string | null
  /** Masked owner id, admin feed only, so a row can be matched to an audit entry by eye. */
  userDisplayHint?: string | null
  /** Administrator feed only: traces the row back to the Kafka envelope that produced it. */
  correlationId?: string | null
  type: NotificationType
  category: NotificationCategory
  severity: NotificationSeverity
  title: string
  message: string
  transactionId?: string | null
  reference?: string | null
  amount?: number | null
  currency?: Currency | null
  read: boolean
  readAt?: string | null
  createdAt: string
}

export interface NotificationFilters {
  type?: NotificationType
  category?: NotificationCategory
  unreadOnly?: boolean
  search?: string
  from?: string
  to?: string
  page?: number
  size?: number
}

export interface UnreadCount {
  unread: number
  byCategory: Record<string, number>
}

export interface NotificationStats {
  total: number
  unread: number
  byType: Record<string, number>
  byCategory: Record<string, number>
  last7Days: SeriesPoint[]
  unreadTrend: SeriesPoint[]
}

export interface NotificationPreference {
  emailEnabled: boolean
  pushEnabled: boolean
  inAppEnabled: boolean
  transferAlerts: boolean
  securityAlerts: boolean
  marketingEmails: boolean
  updatedAt?: string
}

export interface AdminDashboardStats {
  totalUsers: number
  activeAccounts: number
  transactionsToday: number
  transactionVolume: number
  fraudAlerts: number
  userGrowth: SeriesPoint[]
  transactionVolumeSeries: SeriesPoint[]
  successVsFailed: Array<{ label: string; completed: number; failed: number }>
  fraudAlertsSeries: SeriesPoint[]
}

export interface AdminSettings {
  platformName: string
  environment: string
  maintenanceMode: boolean
  transactionApprovalThreshold: number
  maxTransfersPerHour: number
  sessionTimeoutMinutes: number
  fraudScoringEnabled: boolean
  notificationsEnabled: boolean
}
