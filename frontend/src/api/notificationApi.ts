import { http, type RequestOptions } from '@/api/apiClient'
import type {
  Notification,
  NotificationFilters,
  NotificationPreference,
  NotificationStats,
  PageResponse,
  UnreadCount,
} from '@/types'

export const notificationApi = {
  list: (filters: NotificationFilters, options?: RequestOptions) =>
    http.get<PageResponse<Notification>>('/notifications', { ...options, params: filters as never }),

  unread: (limit = 20, options?: RequestOptions) =>
    http.get<Notification[]>('/notifications/unread', { ...options, params: { limit } }),

  unreadCount: (options?: RequestOptions) =>
    http.get<UnreadCount>('/notifications/unread-count', options),

  get: (id: string, options?: RequestOptions) =>
    http.get<Notification>(`/notifications/${id}`, options),

  markRead: (id: string, options?: RequestOptions) =>
    http.patch<Notification>(`/notifications/${id}/read`, undefined, options),

  markAllRead: (options?: RequestOptions) =>
    http.patch<{ updated: number }>('/notifications/read-all', undefined, options),

  stats: (options?: RequestOptions) => http.get<NotificationStats>('/notifications/stats/summary', options),

  preferences: (options?: RequestOptions) =>
    http.get<NotificationPreference>('/notifications/preferences', options),

  updatePreferences: (payload: NotificationPreference, options?: RequestOptions) =>
    http.put<NotificationPreference>('/notifications/preferences', payload, options),

  adminFeed: (
    filters: { userId?: string; page?: number; size?: number },
    options?: RequestOptions,
  ) =>
    http.get<PageResponse<Notification>>('/notifications/admin/feed', {
      ...options,
      params: filters as never,
    }),
}
