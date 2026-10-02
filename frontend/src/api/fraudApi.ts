import { http, type RequestOptions } from '@/api/apiClient'
import type {
  FraudAlert,
  FraudReviewRequest,
  FraudStatsSummary,
  PageResponse,
} from '@/types'

export interface FraudFilters {
  status?: string
  riskLevel?: string
  search?: string
  from?: string
  to?: string
  page?: number
  size?: number
}

export const fraudApi = {
  list: (filters: FraudFilters, options?: RequestOptions) =>
    http.get<PageResponse<FraudAlert>>('/fraud/alerts', { ...options, params: filters as never }),

  unresolved: (options?: RequestOptions) => http.get<FraudAlert[]>('/fraud/alerts/unresolved', options),

  get: (id: string, options?: RequestOptions) => http.get<FraudAlert>(`/fraud/alerts/${id}`, options),

  byTransaction: (transactionId: string, options?: RequestOptions) =>
    http.get<FraudAlert>(`/fraud/alerts/transaction/${transactionId}`, options),

  startReview: (id: string, payload: FraudReviewRequest = {}, options?: RequestOptions) =>
    http.patch<FraudAlert>(`/fraud/alerts/${id}/review`, payload, options),

  markSafe: (id: string, payload: FraudReviewRequest = {}, options?: RequestOptions) =>
    http.patch<FraudAlert>(`/fraud/alerts/${id}/safe`, payload, options),

  confirmFraud: (id: string, payload: FraudReviewRequest = {}, options?: RequestOptions) =>
    http.patch<FraudAlert>(`/fraud/alerts/${id}/confirm`, payload, options),

  blockAccount: (id: string, payload: FraudReviewRequest = {}, options?: RequestOptions) =>
    http.patch<FraudAlert>(`/fraud/alerts/${id}/block-account`, payload, options),

  stats: (options?: RequestOptions) => http.get<FraudStatsSummary>('/fraud/stats/summary', options),
}
