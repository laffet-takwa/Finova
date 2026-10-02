import { AppError } from '@/utils/errors'
import { db, accountByNumber, beneficiaryName } from './database'
import { DEMO_USERS, daysAgo, delay, nextId } from './seed'
import type {
  Account,
  AccountBalance,
  AccountFilters,
  AccountStatsSummary,
  AccountStatusRequest,
  AuditLogEntry,
  BeneficiaryLookup,
  CreateAccountRequest,
  NotificationPreference,
  PageResponse,
  SecurityStatus,
  SeriesPoint,
  UpdateProfileRequest,
  UserStatsSummary,
  UserSummary,
} from '@/types'

function fail(status: number, code: string, message: string): never {
  throw new AppError({ status, code, message })
}

function requireSession(): void {
  if (!localStorage.getItem('finova.mock.session')) {
    fail(401, 'UNAUTHENTICATED', 'Your session has expired. Please sign in again.')
  }
}

function currentUserId(): string {
  return localStorage.getItem('finova.mock.activeUserId') ?? DEMO_USERS.TAKWA.id
}

function isAdminSession(): boolean {
  const raw = localStorage.getItem('finova.user')
  if (!raw) return false
  return (JSON.parse(raw) as { role: string }).role === 'ADMIN'
}

function paginate<T>(content: T[], page = 0, size = 20): PageResponse<T> {
  const totalElements = content.length
  const totalPages = size <= 0 ? 0 : Math.ceil(totalElements / size)
  const start = page * size
  return {
    content: content.slice(start, start + size),
    page,
    size,
    totalElements,
    totalPages,
    first: page === 0,
    last: totalPages === 0 || page >= totalPages - 1,
  }
}

function series(days: number, seed: (index: number) => number, label: (index: number) => string): SeriesPoint[] {
  return Array.from({ length: days }, (_, index) => ({
    label: label(days - 1 - index),
    count: seed(days - 1 - index),
  }))
}

export const mockAccountApi = {
  async list(): Promise<Account[]> {
    await delay()
    requireSession()
    return db.accounts.filter((account) => account.userId === currentUserId())
  },

  async get(id: string): Promise<Account> {
    await delay()
    requireSession()
    const account = db.accounts.find((item) => item.id === id)
    if (!account) fail(404, 'ACCOUNT_NOT_FOUND', 'The selected account was not found.')
    if (!isAdminSession() && account.userId !== currentUserId()) {
      fail(403, 'ACCESS_DENIED', 'You do not have access to this resource.')
    }
    return account
  },

  async create(payload: CreateAccountRequest): Promise<Account> {
    await delay(320, 620)
    requireSession()
    const existing = db.accounts.filter((account) => account.userId === currentUserId())
    if (existing.length >= 3) {
      fail(422, 'ACCOUNT_LIMIT_REACHED', 'You have reached the maximum number of accounts.')
    }
    const duplicate = existing.some(
      (account) => account.accountType === payload.accountType && account.currency === payload.currency,
    )
    if (duplicate) {
      fail(409, 'DUPLICATE_RESOURCE', `You already have a ${payload.accountType.toLowerCase()} account in ${payload.currency}.`)
    }

    const digits = String(Math.floor(1000000000 + Math.random() * 8999999999))
    const prefix = payload.currency === 'TND' ? 'TN58' : payload.currency === 'EUR' ? 'EU76' : 'US12'
    const accountNumber = `${prefix} ${digits.slice(0, 4)} ${digits.slice(4, 8)} ${digits.slice(8, 12)} ${digits.slice(12, 16)} ${digits.slice(16, 19)}`
    const openingBalance = payload.openingBalance ?? (payload.accountType === 'SAVINGS' ? 5000 : 2500)

    const account: Account = {
      id: nextId('acc'),
      accountNumber,
      maskedAccountNumber: `•••• •••• ${digits.slice(-4)}`,
      userId: currentUserId(),
      accountType: payload.accountType,
      currency: payload.currency,
      balance: openingBalance,
      availableBalance: openingBalance,
      status: 'ACTIVE',
      nickname: payload.nickname?.trim() || (payload.accountType === 'SAVINGS' ? 'Savings Account' : 'Everyday Account'),
      iban: `${prefix}1${digits}`,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    }

    db.accounts = [account, ...db.accounts]
    return account
  },

  async balance(id: string): Promise<AccountBalance> {
    await delay(160, 300)
    const account = await this.get(id)
    return {
      accountId: account.id,
      accountNumber: account.maskedAccountNumber,
      currency: account.currency,
      balance: account.balance,
      availableBalance: account.availableBalance,
      status: account.status,
      asOf: new Date().toISOString(),
    }
  },

  async updateStatus(id: string, payload: AccountStatusRequest): Promise<Account> {
    await delay(260, 480)
    requireSession()
    if (!isAdminSession()) fail(403, 'ACCESS_DENIED', 'You do not have access to this resource.')
    const index = db.accounts.findIndex((item) => item.id === id)
    if (index === -1) fail(404, 'ACCOUNT_NOT_FOUND', 'The selected account was not found.')

    const current = db.accounts[index]
    if (current.status === payload.status) return current
    if (current.status === 'CLOSED') fail(405, 'OPERATION_NOT_ALLOWED', 'A closed account cannot change status.')
    if (payload.status === 'CLOSED' && current.balance > 0) {
      fail(405, 'OPERATION_NOT_ALLOWED', 'An account with a non-zero balance cannot be closed.')
    }

    const updated: Account = {
      ...current,
      status: payload.status,
      availableBalance: payload.status === 'ACTIVE' ? current.balance : 0,
      updatedAt: new Date().toISOString(),
    }
    db.accounts[index] = updated
    return updated
  },

  async lookupBeneficiary(accountNumber: string): Promise<BeneficiaryLookup> {
    await delay(240, 460)
    requireSession()
    const account = accountByNumber(accountNumber)
    if (!account) fail(404, 'ACCOUNT_NOT_FOUND', 'We could not find an account with that number.')

    const own = db.accounts.find(
      (item) => item.userId === currentUserId() && item.id === account.id,
    )
    if (own) {
      fail(400, 'VALIDATION_ERROR', 'The source and destination accounts must be different.')
    }

    return {
      accountNumber: account.accountNumber,
      maskedAccountNumber: account.maskedAccountNumber,
      accountType: account.accountType,
      currency: account.currency,
      holderDisplayName: beneficiaryName(accountNumber),
      status: account.status,
      bankName: 'Finova Bank',
    }
  },

  async adminStats(): Promise<AccountStatsSummary> {
    await delay(220, 420)
    const active = db.accounts.filter((account) => account.status === 'ACTIVE')
    return {
      totalAccounts: 14280,
      activeAccounts: active.length + 14266,
      blockedAccounts: db.accounts.filter((account) => account.status === 'BLOCKED').length + 9,
      closedAccounts: 5,
      totalBalanceByCurrency: { TND: 48_320_750, EUR: 1_284_400, USD: 962_180 },
      accountsByType: { CHECKING: 8_940, SAVINGS: 5_340 },
      newAccountsLast30Days: 214,
      growthSeries: series(30, (index) => 5 + Math.round(index / 4) + ((index * 7) % 5), (index) => {
        const date = new Date()
        date.setDate(date.getDate() - index)
        return date.toLocaleDateString('en-GB', { day: '2-digit', month: 'short' })
      }),
      dailyBalanceSeries: series(30, (index) => 47_500_000 + index * 12_400 + ((index * 13) % 9) * 1000, (index) => {
        const date = new Date()
        date.setDate(date.getDate() - index)
        return date.toLocaleDateString('en-GB', { day: '2-digit', month: 'short' })
      }),
    }
  },

  async adminList(filters: AccountFilters): Promise<PageResponse<Account>> {
    await delay(240, 460)
    let rows = [...db.accounts]
    if (filters.userId) rows = rows.filter((account) => account.userId === filters.userId)
    if (filters.status) rows = rows.filter((account) => account.status === filters.status)
    if (filters.accountType) rows = rows.filter((account) => account.accountType === filters.accountType)
    if (filters.currency) rows = rows.filter((account) => account.currency === filters.currency)
    if (filters.search) {
      const needle = filters.search.toLowerCase()
      rows = rows.filter(
        (account) =>
          account.accountNumber.toLowerCase().includes(needle) ||
          account.userId.toLowerCase().includes(needle) ||
          (account.nickname ?? '').toLowerCase().includes(needle),
      )
    }
    if (filters.minBalance !== undefined) rows = rows.filter((account) => account.balance >= filters.minBalance!)
    if (filters.maxBalance !== undefined) rows = rows.filter((account) => account.balance <= filters.maxBalance!)
    return paginate(rows, filters.page ?? 0, filters.size ?? 20)
  },
}

export const mockUserApi = {
  async me(): Promise<import('@/types').AuthUser> {
    await delay(120, 240)
    const raw = localStorage.getItem('finova.user')
    if (!raw) fail(401, 'UNAUTHENTICATED', 'Your session has expired. Please sign in again.')
    return JSON.parse(raw) as import('@/types').AuthUser
  },

  async updateProfile(payload: UpdateProfileRequest): Promise<import('@/types').AuthUser> {
    await delay(300, 560)
    const raw = localStorage.getItem('finova.user')
    if (!raw) fail(401, 'UNAUTHENTICATED', 'Your session has expired. Please sign in again.')
    const updated = { ...(JSON.parse(raw) as import('@/types').AuthUser), ...payload }
    localStorage.setItem('finova.user', JSON.stringify(updated))
    return updated
  },

  async changePassword(): Promise<void> {
    await delay(360, 640)
    requireSession()
  },

  async securityStatus(): Promise<SecurityStatus> {
    await delay(240, 440)
    return {
      passwordProtected: true,
      twoFactorEnabled: false,
      mfaConfigured: false,
      lastPasswordChange: daysAgo(63, 14, 8),
      locationSource: 'NOT_PROVIDED_BY_BACKEND',
      recentLogins: [
        { occurredAt: daysAgo(0, 19, 43), ipAddress: '197.0.14.52', device: 'Chrome on Windows', userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)', location: null, result: 'SUCCESS' },
        { occurredAt: daysAgo(1, 8, 12), ipAddress: '197.0.14.52', device: 'Chrome on Windows', userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)', location: null, result: 'SUCCESS' },
        { occurredAt: daysAgo(3, 22, 5), ipAddress: '41.226.11.8', device: 'Safari on iPhone', userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X)', location: null, result: 'SUCCESS' },
        { occurredAt: daysAgo(4, 3, 19), ipAddress: '185.14.77.203', device: 'Firefox on Ubuntu', userAgent: 'Mozilla/5.0 (X11; Linux x86_64)', location: null, result: 'FAILURE' },
      ],
    }
  },

  async adminList(filters: { search?: string; status?: string; page?: number; size?: number }): Promise<PageResponse<UserSummary>> {
    await delay(260, 480)
    const all: UserSummary[] = Object.values(DEMO_USERS).map((user, index) => ({
      id: user.id,
      firstName: user.firstName,
      lastName: user.lastName,
      email: user.email,
      phone: user.phone,
      role: user.role,
      status: user.status,
      createdAt: user.createdAt,
      lastLoginAt: index === 0 ? daysAgo(0, 8, 5) : daysAgo(index + 1, 19, 12),
    }))

    let rows = all
    if (filters.search) {
      const needle = filters.search.toLowerCase()
      rows = rows.filter(
        (user) =>
          user.email.toLowerCase().includes(needle) ||
          `${user.firstName} ${user.lastName}`.toLowerCase().includes(needle),
      )
    }
    if (filters.status) rows = rows.filter((user) => user.status === filters.status)
    return paginate(rows, filters.page ?? 0, filters.size ?? 20)
  },

  async adminGet(id: string): Promise<UserSummary> {
    const result = await this.adminList({})
    const user = result.content.find((item) => item.id === id)
    if (!user) fail(404, 'USER_NOT_FOUND', 'The selected user was not found.')
    return user
  },

  async adminUpdateStatus(id: string, status: 'ACTIVE' | 'BLOCKED'): Promise<UserSummary> {
    await delay(280, 520)
    requireSession()
    if (!isAdminSession()) fail(403, 'ACCESS_DENIED', 'You do not have access to this resource.')
    const user = await this.adminGet(id)
    return { ...user, status }
  },

  async adminStats(): Promise<UserStatsSummary> {
    await delay(200, 400)
    return {
      totalUsers: 12540,
      activeUsers: 12506,
      blockedUsers: 34,
      newUsersThisMonth: 186,
      growthSeries: series(12, (index) => 98 + index * 9 + ((index * 11) % 7) * 3, (index) => {
        const date = new Date()
        date.setMonth(date.getMonth() - 11 + index)
        return date.toLocaleDateString('en-GB', { month: 'short', year: '2-digit' })
      }),
    }
  },

  async auditLogs(filters: {
    action?: string
    result?: string
    search?: string
    from?: string
    to?: string
    page?: number
    size?: number
  }): Promise<PageResponse<AuditLogEntry>> {
    await delay(260, 480)
    const actions = [
      'LOGIN_SUCCESS', 'LOGIN_FAILED', 'TRANSFER_CREATED', 'TRANSFER_COMPLETED',
      'TRANSFER_FAILED', 'FRAUD_DETECTED', 'ACCOUNT_BLOCKED', 'PROFILE_UPDATED',
      'USER_REGISTERED', 'LOGOUT', 'FRAUD_REVIEWED', 'ACCOUNT_STATUS_CHANGED',
    ]
    const rows: AuditLogEntry[] = Array.from({ length: 240 }, (_, index) => {
      const action = actions[index % actions.length]
      const failed = action.includes('FAILED')
      return {
        id: `audit-${String(index + 1).padStart(5, '0')}`,
        action,
        userId: index % 3 === 0 ? DEMO_USERS.TAKWA.id : DEMO_USERS.INES.id,
        resource: action.startsWith('TRANSFER') ? 'transaction' : action.includes('ACCOUNT') ? 'account' : action.startsWith('LOGIN') ? 'session' : 'user',
        resourceId: action.startsWith('TRANSFER') ? `TX-202609${String((index % 28) + 1).padStart(2, '0')}-${String(index).padStart(5, '0')}` : `res-${index}`,
        ipAddress: index % 4 === 0 ? '41.226.11.8' : '197.0.14.52',
        correlationId: `mock-${String(index).padStart(5, '0')}`,
        result: failed ? 'FAILURE' : 'SUCCESS',
        service: action.startsWith('TRANSFER') ? 'transaction-service' : 'user-service',
        message: `${action.replace(/_/g, ' ').toLowerCase()} recorded for demo review`,
        metadata: { source: 'mock-data' },
        createdAt: daysAgo(index % 30, 9 + (index % 8), (index * 7) % 60),
      }
    })

    let filtered = rows
    if (filters.action) filtered = filtered.filter((row) => row.action === filters.action)
    if (filters.result) filtered = filtered.filter((row) => row.result === filters.result)
    if (filters.search) {
      const needle = filters.search.toLowerCase()
      filtered = filtered.filter(
        (row) => row.action.toLowerCase().includes(needle) || (row.resourceId ?? '').toLowerCase().includes(needle),
      )
    }
    return paginate(filtered, filters.page ?? 0, filters.size ?? 20)
  },
}

export const mockNotificationPreferences = {
  async get(): Promise<NotificationPreference> {
    await delay(200, 380)
    return (
      db.preferences.get(currentUserId()) ?? {
        emailEnabled: true,
        pushEnabled: true,
        inAppEnabled: true,
        transferAlerts: true,
        securityAlerts: true,
        marketingEmails: false,
      }
    )
  },

  async update(payload: NotificationPreference): Promise<NotificationPreference> {
    await delay(280, 520)
    db.preferences.set(currentUserId(), payload)
    return payload
  },
}