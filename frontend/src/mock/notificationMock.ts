import { AppError } from '@/utils/errors'
import { db } from './database'
import { delay } from './seed'
import type {
  Notification,
  NotificationFilters,
  NotificationPreference,
  NotificationStats,
  PageResponse,
  UnreadCount,
} from '@/types'

function fail(status: number, code: string, message: string): never {
  throw new AppError({ status, code, message })
}

function currentUserId(): string {
  return localStorage.getItem('finova.mock.activeUserId') ?? 'demo-takwa'
}

function requireSession(): void {
  if (!localStorage.getItem('finova.mock.session')) {
    fail(401, 'UNAUTHENTICATED', 'Your session has expired. Please sign in again.')
  }
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

function forUser(): Notification[] {
  return db.notifications
    .filter((item) => item.userId === currentUserId())
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
}

export const mockNotificationApi = {
  async list(filters: NotificationFilters): Promise<PageResponse<Notification>> {
    await delay(240, 460)
    requireSession()
    let rows = forUser()
    if (filters.type) rows = rows.filter((item) => item.type === filters.type)
    if (filters.category) rows = rows.filter((item) => item.category === filters.category)
    if (filters.unreadOnly) rows = rows.filter((item) => !item.read)
    if (filters.search) {
      const needle = filters.search.toLowerCase()
      rows = rows.filter((item) => `${item.title} ${item.message}`.toLowerCase().includes(needle))
    }
    if (filters.from) rows = rows.filter((item) => item.createdAt >= new Date(filters.from!).toISOString())
    return paginate(rows, filters.page ?? 0, filters.size ?? 20)
  },

  async unread(limit = 20): Promise<Notification[]> {
    await delay(180, 340)
    requireSession()
    return forUser().filter((item) => !item.read).slice(0, limit)
  },

  async unreadCount(): Promise<UnreadCount> {
    await delay(120, 240)
    requireSession()
    const rows = forUser().filter((item) => !item.read)
    const byCategory: Record<string, number> = { TRANSACTIONS: 0, SECURITY: 0, SYSTEM: 0 }
    for (const item of rows) byCategory[item.category] = (byCategory[item.category] ?? 0) + 1
    return { unread: rows.length, byCategory }
  },

  async get(id: string): Promise<Notification> {
    await delay(160, 300)
    requireSession()
    const item = db.notifications.find(
      (entry) => entry.id === id && entry.userId === currentUserId(),
    )
    if (!item) fail(404, 'NOTIFICATION_NOT_FOUND', 'The selected notification was not found.')
    return item
  },

  async markRead(id: string): Promise<Notification> {
    await delay(160, 300)
    const item = await this.get(id)
    if (!item.read) {
      item.read = true
      item.readAt = new Date().toISOString()
    }
    return item
  },

  async markAllRead(): Promise<{ updated: number }> {
    await delay(220, 420)
    requireSession()
    let updated = 0
    for (const item of db.notifications) {
      if (item.userId === currentUserId() && !item.read) {
        item.read = true
        item.readAt = new Date().toISOString()
        updated += 1
      }
    }
    return { updated }
  },

  async stats(): Promise<NotificationStats> {
    await delay(220, 400)
    requireSession()
    const rows = forUser()
    const byType: Record<string, number> = {}
    const byCategory: Record<string, number> = {}
    for (const item of rows) {
      byType[item.type] = (byType[item.type] ?? 0) + 1
      byCategory[item.category] = (byCategory[item.category] ?? 0) + 1
    }

    return {
      total: rows.length,
      unread: rows.filter((item) => !item.read).length,
      byType,
      byCategory,
      last7Days: Array.from({ length: 7 }, (_, offset) => {
        const date = new Date()
        date.setDate(date.getDate() - (6 - offset))
        const day = date.toISOString().slice(0, 10)
        return {
          label: date.toLocaleDateString('en-GB', { weekday: 'short' }),
          count: rows.filter((item) => item.createdAt.slice(0, 10) === day).length,
        }
      }),
      unreadTrend: Array.from({ length: 7 }, (_, offset) => {
        const date = new Date()
        date.setDate(date.getDate() - (6 - offset))
        return {
          label: date.toLocaleDateString('en-GB', { weekday: 'short' }),
          count: Math.max(0, 3 - offset + ((offset * 2) % 4)),
        }
      }),
    }
  },

  async preferences(): Promise<NotificationPreference> {
    await delay(200, 360)
    requireSession()
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

  async updatePreferences(payload: NotificationPreference): Promise<NotificationPreference> {
    await delay(280, 500)
    db.preferences.set(currentUserId(), payload)
    return payload
  },

  async adminFeed(filters: { userId?: string; page?: number; size?: number }): Promise<PageResponse<Notification>> {
    await delay(240, 440)
    const raw = localStorage.getItem('finova.user')
    if (!raw || (JSON.parse(raw) as { role: string }).role !== 'ADMIN') {
      fail(403, 'ACCESS_DENIED', 'Administrator access is required.')
    }
    let rows = [...db.notifications].sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    if (filters.userId) rows = rows.filter((item) => item.userId === filters.userId)
    return paginate(rows, filters.page ?? 0, filters.size ?? 20)
  },
}