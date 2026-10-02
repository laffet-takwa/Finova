import { http, type RequestOptions } from '@/api/apiClient'
import type {
  Account,
  AccountBalance,
  AccountFilters,
  AccountStatsSummary,
  AccountStatusRequest,
  BeneficiaryLookup,
  CreateAccountRequest,
  PageResponse,
  UserStatsSummary,
  UserSummary,
  AuditLogEntry,
  SecurityStatus,
  UpdateProfileRequest,
  Role,
  UserStatus,
} from '@/types'

export const accountApi = {
  list: (options?: RequestOptions) => http.get<Account[]>('/accounts', options),

  get: (id: string, options?: RequestOptions) => http.get<Account>(`/accounts/${id}`, options),

  create: (payload: CreateAccountRequest, options?: RequestOptions) =>
    http.post<Account>('/accounts', payload, options),

  balance: (id: string, options?: RequestOptions) =>
    http.get<AccountBalance>(`/accounts/${id}/balance`, options),

  updateStatus: (id: string, payload: AccountStatusRequest, options?: RequestOptions) =>
    http.put<Account>(`/accounts/${id}/status`, payload, options),

  lookupBeneficiary: (accountNumber: string, options?: RequestOptions) =>
    http.get<BeneficiaryLookup>('/accounts/lookup', { ...options, params: { accountNumber } }),

  adminStats: (options?: RequestOptions) => http.get<AccountStatsSummary>('/accounts/stats/summary', options),

  adminList: (filters: AccountFilters, options?: RequestOptions) =>
    http.get<PageResponse<Account>>('/accounts/admin/all', { ...options, params: filters as never }),
}

export interface AdminUserFilters {
  search?: string
  role?: Role
  status?: UserStatus
  page?: number
  size?: number
  sort?: string
}

/**
 * Every administrative route sits under the `admin` path segment on purpose:
 * the api-gateway enforces `ROLE_ADMIN` on any path containing `admin`, so a
 * CUSTOMER token is rejected at the edge before the request reaches the
 * service. Keep that segment — moving these routes back to bare `/users/…`
 * would silently drop the perimeter check.
 */
export const userApi = {
  me: (options?: RequestOptions) => http.get<import('@/types').AuthUser>('/users/me', options),

  updateProfile: (payload: UpdateProfileRequest, options?: RequestOptions) =>
    http.put<import('@/types').AuthUser>('/users/me', payload, options),

  changePassword: (
    payload: { currentPassword: string; newPassword: string },
    options?: RequestOptions,
  ) => http.post<void>('/users/me/password', payload, options),

  securityStatus: (options?: RequestOptions) =>
    http.get<SecurityStatus>('/users/me/security', options),

  adminList: (filters: AdminUserFilters, options?: RequestOptions) =>
    http.get<PageResponse<UserSummary>>('/users/admin', { ...options, params: filters as never }),

  adminGet: (id: string, options?: RequestOptions) =>
    http.get<UserSummary>(`/users/admin/${id}`, options),

  adminUpdateStatus: (id: string, status: UserStatus, reason?: string, options?: RequestOptions) =>
    http.put<UserSummary>(`/users/admin/${id}/status`, { status, reason }, options),

  adminStats: (options?: RequestOptions) =>
    http.get<UserStatsSummary>('/users/admin/stats', options),

  auditLogs: (
    filters: {
      userId?: string
      action?: string
      result?: string
      search?: string
      from?: string
      to?: string
      page?: number
      size?: number
    },
    options?: RequestOptions,
  ) =>
    http.get<PageResponse<AuditLogEntry>>('/users/admin/audit-logs', {
      ...options,
      params: filters as never,
    }),
}
