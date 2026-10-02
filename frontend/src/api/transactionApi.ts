import { http, newIdempotencyKey, type RequestOptions } from '@/api/apiClient'
import type {
  AdminTransactionStats,
  CreateTransactionRequest,
  PageResponse,
  Transaction,
  TransactionFilters,
  TransactionSummary,
  TransactionTimeline,
} from '@/types'

export interface CreateTransactionResult {
  transaction: Transaction
  idempotentReplay: boolean
}

export const transactionApi = {
  create: (
    payload: CreateTransactionRequest,
    options?: RequestOptions & { idempotencyKey?: string },
  ): Promise<CreateTransactionResult> => {
    const { idempotencyKey, ...requestOptions } = options ?? {}
    return http
      .post<Transaction>('/transactions', payload, {
        ...requestOptions,
        headers: {
          ...(requestOptions.headers ?? {}),
          'Idempotency-Key': idempotencyKey ?? newIdempotencyKey(),
        },
      })
      .then((transaction) => ({ transaction, idempotentReplay: false }))
  },

  list: (filters: TransactionFilters, options?: RequestOptions) =>
    http.get<PageResponse<Transaction>>('/transactions', { ...options, params: filters as never }),

  get: (id: string, options?: RequestOptions) => http.get<Transaction>(`/transactions/${id}`, options),

  timeline: (id: string, options?: RequestOptions) =>
    http.get<TransactionTimeline>(`/transactions/${id}/timeline`, options),

  summary: (options?: RequestOptions) => http.get<TransactionSummary>('/transactions/summary', options),

  adminStats: (options?: RequestOptions) =>
    http.get<AdminTransactionStats>('/transactions/stats/summary', options),

  adminList: (filters: TransactionFilters, options?: RequestOptions) =>
    http.get<PageResponse<Transaction>>('/transactions/admin/all', {
      ...options,
      params: filters as never,
    }),
}
