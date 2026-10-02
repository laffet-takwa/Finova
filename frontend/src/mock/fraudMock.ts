import { AppError } from '@/utils/errors'
import { db } from './database'
import { daysAgo, delay, masked, nextId } from './seed'
import type { FraudAlert, FraudStatsSummary, PageResponse } from '@/types'

function fail(status: number, code: string, message: string): never {
  throw new AppError({ status, code, message })
}

function requireAdmin(): void {
  const raw = localStorage.getItem('finova.user')
  if (!localStorage.getItem('finova.mock.session')) {
    fail(401, 'UNAUTHENTICATED', 'Your session has expired. Please sign in again.')
  }
  if (!raw || (JSON.parse(raw) as { role: string }).role !== 'ADMIN') {
    fail(403, 'ACCESS_DENIED', 'Administrator access is required for fraud operations.')
  }
}

interface AlertSeed {
  score: number
  level: FraudAlert['riskLevel']
  status: FraudAlert['status']
  amount: number
  days: number
  account: string
  reasons: string[]
  rules: string[]
  note?: string
}

const ALERT_SEEDS: AlertSeed[] = [
  { score: 88, level: 'HIGH', status: 'OPEN', amount: 15000, days: 1, account: 'TN58 1000 0123 4567 8901 23', reasons: ['Large transaction amount', 'Unusual transaction pattern'], rules: ['LARGE_AMOUNT', 'UNUSUAL_PATTERN'] },
  { score: 94, level: 'HIGH', status: 'OPEN', amount: 22500, days: 1, account: 'TN58 2000 0789 1234 5678 90', reasons: ['Large transaction amount', '7 transactions from this account within the last 60 seconds'], rules: ['LARGE_AMOUNT', 'BURST_VELOCITY'] },
  { score: 79, level: 'HIGH', status: 'OPEN', amount: 11800, days: 2, account: 'TN58 3000 0567 8899 0011 22', reasons: ['Large transaction amount', 'Round amount structuring probe'], rules: ['LARGE_AMOUNT', 'ROUND_AMOUNT_PROBE'] },
  { score: 86, level: 'HIGH', status: 'UNDER_REVIEW', amount: 16400, days: 2, account: 'TN58 1000 0123 4567 8901 23', reasons: ['Large transaction amount', 'Beneficiary not paid before in 7 days'], rules: ['LARGE_AMOUNT', 'NEW_ACCOUNT_RECIPIENT'] },
  { score: 72, level: 'HIGH', status: 'OPEN', amount: 10850, days: 3, account: 'TN58 2000 0345 6677 8811 05', reasons: ['Large transaction amount'], rules: ['LARGE_AMOUNT'] },
  { score: 91, level: 'HIGH', status: 'OPEN', amount: 27500, days: 3, account: 'TN58 2000 0789 1234 5678 90', reasons: ['Large transaction amount', 'Unusual transaction pattern', 'Multiple recent transfers'], rules: ['LARGE_AMOUNT', 'UNUSUAL_PATTERN', 'BURST_VELOCITY'] },
  { score: 76, level: 'HIGH', status: 'CONFIRMED', amount: 13400, days: 5, account: 'TN58 3000 0567 8899 0011 22', reasons: ['Large transaction amount', 'Unusual transaction pattern'], rules: ['LARGE_AMOUNT', 'UNUSUAL_PATTERN'], note: 'Confirmed as fraud. Account blocked pending investigation.' },
  { score: 64, level: 'MEDIUM', status: 'OPEN', amount: 4200, days: 1, account: 'TN58 1000 0123 4567 8901 23', reasons: ['6 transactions from this account within the last 60 seconds'], rules: ['BURST_VELOCITY'] },
  { score: 58, level: 'MEDIUM', status: 'OPEN', amount: 2600, days: 2, account: 'TN58 2000 0345 6677 8811 05', reasons: ['Amount is 4.2x the sender average'], rules: ['UNUSUAL_PATTERN'] },
  { score: 52, level: 'MEDIUM', status: 'OPEN', amount: 1850, days: 2, account: 'TN58 3000 0567 8899 0011 22', reasons: ['Beneficiary not paid before in 7 days'], rules: ['NEW_ACCOUNT_RECIPIENT'] },
  { score: 61, level: 'MEDIUM', status: 'UNDER_REVIEW', amount: 3400, days: 4, account: 'TN58 2000 0789 1234 5678 90', reasons: ['Amount is 3.6x the sender average'], rules: ['UNUSUAL_PATTERN'] },
  { score: 47, level: 'MEDIUM', status: 'OPEN', amount: 1250, days: 5, account: 'TN58 1000 0123 4567 8901 23', reasons: ['Round amount structuring probe'], rules: ['ROUND_AMOUNT_PROBE'] },
  { score: 55, level: 'MEDIUM', status: 'OPEN', amount: 980, days: 6, account: 'TN58 2000 0345 6677 8811 05', reasons: ['Beneficiary not paid before in 7 days'], rules: ['NEW_ACCOUNT_RECIPIENT'] },
  { score: 44, level: 'MEDIUM', status: 'OPEN', amount: 720, days: 7, account: 'TN58 3000 0567 8899 0011 22', reasons: ['Amount is 3.1x the sender average'], rules: ['UNUSUAL_PATTERN'] },
  { score: 68, level: 'MEDIUM', status: 'OPEN', amount: 5600, days: 8, account: 'TN58 2000 0789 1234 5678 90', reasons: ['8 transactions from this account within the last 60 seconds'], rules: ['BURST_VELOCITY'] },
  { score: 38, level: 'MEDIUM', status: 'OPEN', amount: 430, days: 9, account: 'TN58 1000 0123 4567 8901 23', reasons: ['Round amount structuring probe'], rules: ['ROUND_AMOUNT_PROBE'] },
  { score: 71, level: 'MEDIUM', status: 'OPEN', amount: 6900, days: 11, account: 'TN58 3000 0567 8899 0011 22', reasons: ['Amount is 3.4x the sender average'], rules: ['UNUSUAL_PATTERN'] },
  { score: 22, level: 'LOW', status: 'SAFE', amount: 320, days: 0, account: 'TN58 1000 0123 4567 8901 23', reasons: ['Within normal account behaviour'], rules: [] },
  { score: 14, level: 'LOW', status: 'SAFE', amount: 85, days: 1, account: 'TN58 2000 0345 6677 8811 05', reasons: ['Within normal account behaviour'], rules: [] },
  { score: 9, level: 'LOW', status: 'SAFE', amount: 15.99, days: 0, account: 'TN58 1000 0123 4567 8901 23', reasons: ['Within normal account behaviour'], rules: [] },
  { score: 27, level: 'LOW', status: 'SAFE', amount: 640, days: 4, account: 'TN58 2000 0789 1234 5678 90', reasons: ['Within normal account behaviour'], rules: [] },
  { score: 18, level: 'LOW', status: 'SAFE', amount: 210, days: 6, account: 'TN58 3000 0567 8899 0011 22', reasons: ['Within normal account behaviour'], rules: [] },
]

const REVIEWED_BY = ['demo-admin', 'demo-admin', 'demo-admin', 'Amine Ben Salah']

function buildAlert(seed: AlertSeed, index: number): FraudAlert {
  const createdAt = daysAgo(seed.days, 9 + (index % 9), (index * 11) % 60)
  const reviewed = seed.status === 'SAFE' || seed.status === 'CONFIRMED' || seed.status === 'UNDER_REVIEW'
  return {
    id: `alert-${String(index + 1).padStart(4, '0')}`,
    transactionId: `tx-alert-${String(index + 1).padStart(4, '0')}`,
    reference: `TX-${createdAt.slice(0, 10).replace(/-/g, '')}-${String(1000 + index).padStart(5, '0')}`,
    senderAccountId: db.accounts.find((a) => a.accountNumber === seed.account)?.id ?? 'acc-checking-6789',
    receiverAccountId: 'acc-external-77',
    senderAccountNumber: masked(seed.account),
    senderUserId: db.accounts.find((a) => a.accountNumber === seed.account)?.userId ?? 'demo-takwa',
    amount: seed.amount,
    currency: 'TND',
    riskScore: seed.score,
    riskLevel: seed.level,
    reasons: seed.reasons,
    triggeredRules: seed.rules,
    status: seed.status,
    createdAt,
    updatedAt: reviewed ? daysAgo(Math.max(0, seed.days - 1), 14, 22) : createdAt,
    reviewedAt: reviewed ? daysAgo(Math.max(0, seed.days - 1), 14, 20) : null,
    reviewedBy: reviewed ? REVIEWED_BY[index % REVIEWED_BY.length] : null,
    reviewNote: seed.note ?? (reviewed ? 'Reviewed by the risk desk.' : null),
    timeline: [
      { key: 'TRANSACTION_CREATED', label: 'Transaction created', description: 'Transfer accepted by the transaction service as pending.', at: createdAt },
      { key: 'AMOUNT_VALIDATION', label: 'Amount validation', description: `Amount ${seed.amount.toFixed(3)} TND validated against account limits.`, at: createdAt },
      { key: 'FRAUD_ANALYSIS', label: 'Fraud analysis', description: `${seed.rules.length || 'No'} rule(s) evaluated across amount, velocity and behaviour.`, at: createdAt },
      { key: 'RISK_SCORED', label: `Risk score: ${seed.score}`, description: `Risk band ${seed.level} (${seed.score}/100).`, at: createdAt },
      {
        key: 'ALERT_CREATED',
        label: seed.level === 'HIGH' ? 'Alert created' : 'Assessment recorded',
        description: seed.level === 'HIGH' ? 'Alert raised for administrator review. Funds are held.' : 'No alert required; assessment stored for audit.',
        at: createdAt,
      },
    ],
  }
}

const alerts: FraudAlert[] = ALERT_SEEDS.map(buildAlert)

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

function refresh(): FraudAlert[] {
  const flagged = db.transactions.find((transaction) => transaction.status === 'FLAGGED')
  if (flagged && !alerts.some((alert) => alert.transactionId === flagged.id)) {
    alerts.unshift({
      id: nextId('alert'),
      transactionId: flagged.id,
      reference: flagged.reference,
      senderAccountId: flagged.senderAccountId,
      receiverAccountId: flagged.receiverAccountId,
      senderAccountNumber: flagged.senderAccountNumber,
      senderUserId: 'demo-takwa',
      amount: flagged.amount,
      currency: flagged.currency,
      riskScore: flagged.riskScore ?? 88,
      riskLevel: flagged.riskLevel ?? 'HIGH',
      reasons: flagged.riskReasons ?? ['Large transaction amount'],
      triggeredRules: ['LARGE_AMOUNT'],
      status: 'OPEN',
      createdAt: flagged.createdAt,
      updatedAt: flagged.createdAt,
      reviewedAt: null,
      reviewedBy: null,
      reviewNote: null,
      timeline: [
        { key: 'TRANSACTION_CREATED', label: 'Transaction created', description: 'Transfer accepted as pending.', at: flagged.createdAt },
        { key: 'FRAUD_ANALYSIS', label: 'Fraud analysis', description: 'Rules evaluated.', at: flagged.createdAt },
        { key: 'RISK_SCORED', label: `Risk score: ${flagged.riskScore ?? 88}`, description: 'Risk band HIGH.', at: flagged.createdAt },
        { key: 'ALERT_CREATED', label: 'Alert created', description: 'Funds held for administrator review.', at: flagged.createdAt },
      ],
    })
  }
  return alerts
}

export const mockFraudApi = {
  async list(filters: { status?: string; riskLevel?: string; search?: string; page?: number; size?: number }): Promise<PageResponse<FraudAlert>> {
    await delay(280, 520)
    requireAdmin()
    let rows = refresh()
    if (filters.status) rows = rows.filter((alert) => alert.status === filters.status)
    if (filters.riskLevel) rows = rows.filter((alert) => alert.riskLevel === filters.riskLevel)
    if (filters.search) {
      const needle = filters.search.toLowerCase()
      rows = rows.filter(
        (alert) =>
          alert.reference.toLowerCase().includes(needle) ||
          alert.senderAccountNumber.toLowerCase().includes(needle),
      )
    }
    rows = [...rows].sort((a, b) => b.riskScore - a.riskScore || b.createdAt.localeCompare(a.createdAt))
    return paginate(rows, filters.page ?? 0, filters.size ?? 20)
  },

  async unresolved(): Promise<FraudAlert[]> {
    await delay(240, 440)
    requireAdmin()
    return refresh()
      .filter((alert) => alert.status === 'OPEN' || alert.status === 'UNDER_REVIEW')
      .sort((a, b) => b.riskScore - a.riskScore)
  },

  async get(id: string): Promise<FraudAlert> {
    await delay(200, 380)
    requireAdmin()
    const alert = refresh().find((item) => item.id === id)
    if (!alert) fail(404, 'FRAUD_ALERT_NOT_FOUND', 'The selected fraud alert was not found.')
    return alert
  },

  async byTransaction(transactionId: string): Promise<FraudAlert> {
    await delay(200, 380)
    requireAdmin()
    const alert = refresh().find((item) => item.transactionId === transactionId)
    if (!alert) fail(404, 'FRAUD_ALERT_NOT_FOUND', 'The selected fraud alert was not found.')
    return alert
  },

  async startReview(id: string, payload: { note?: string }): Promise<FraudAlert> {
    await delay(300, 560)
    return this.get(id).then((alert) => {
      if (alert.status === 'SAFE' || alert.status === 'CONFIRMED') {
        fail(409, 'ALERT_ALREADY_REVIEWED', 'This alert has already been reviewed.')
      }
      alert.status = 'UNDER_REVIEW'
      alert.reviewedAt = new Date().toISOString()
      alert.reviewedBy = 'demo-admin'
      alert.reviewNote = payload.note ?? 'Review started.'
      alert.timeline.push({ key: 'REVIEW_STARTED', label: 'Review started', description: alert.reviewNote, at: alert.reviewedAt })
      return alert
    })
  },

  async markSafe(id: string, payload: { note?: string }): Promise<FraudAlert> {
    await delay(420, 760)
    const alert = await this.get(id)
    if (alert.status !== 'OPEN' && alert.status !== 'UNDER_REVIEW') {
      fail(409, 'ALERT_ALREADY_REVIEWED', 'This alert has already been reviewed.')
    }
    alert.status = 'SAFE'
    alert.reviewedAt = new Date().toISOString()
    alert.reviewedBy = 'demo-admin'
    alert.reviewNote = payload.note ?? 'Marked safe — funds released for settlement.'
    alert.timeline.push({ key: 'MARKED_SAFE', label: 'Marked safe by administrator', description: alert.reviewNote, at: alert.reviewedAt })

    const held = db.transactions.find((transaction) => transaction.id === alert.transactionId)
    if (held && held.status === 'FLAGGED') {
      const sender = db.accounts.find((account) => account.id === held.senderAccountId)
      const receiver = db.accounts.find((account) => account.id === held.receiverAccountId)
      if (sender && sender.balance >= held.amount) {
        sender.balance = Number((sender.balance - held.amount).toFixed(3))
        sender.availableBalance = sender.balance
        if (receiver) {
          receiver.balance = Number((receiver.balance + held.amount).toFixed(3))
          receiver.availableBalance = receiver.balance
        }
        held.status = 'COMPLETED'
        held.completedAt = new Date().toISOString()
      }
    }
    return alert
  },

  async confirmFraud(id: string, payload: { note?: string }): Promise<FraudAlert> {
    await delay(360, 640)
    const alert = await this.get(id)
    if (alert.status === 'CONFIRMED') {
      fail(409, 'ALERT_ALREADY_REVIEWED', 'This alert has already been reviewed.')
    }
    alert.status = 'CONFIRMED'
    alert.reviewedAt = new Date().toISOString()
    alert.reviewedBy = 'demo-admin'
    alert.reviewNote = payload.note ?? 'Confirmed as fraudulent. Funds remain held for recovery.'
    alert.timeline.push({ key: 'CONFIRMED_FRAUD', label: 'Confirmed as fraud', description: alert.reviewNote, at: alert.reviewedAt })
    return alert
  },

  async blockAccount(id: string, payload: { note?: string }): Promise<FraudAlert> {
    await delay(420, 780)
    const alert = await this.get(id)
    const account = db.accounts.find((item) => item.id === alert.senderAccountId)
    if (account && account.status !== 'BLOCKED') {
      account.status = 'BLOCKED'
      account.availableBalance = 0
      account.updatedAt = new Date().toISOString()
      db.notifications.unshift({
        id: nextId('ntf'),
        userId: account.userId,
        type: 'ACCOUNT_BLOCKED',
        category: 'SECURITY',
        severity: 'DANGER',
        title: 'Account blocked',
        message: `Account ${account.maskedAccountNumber} has been blocked. Contact support for more information.`,
        transactionId: alert.transactionId,
        reference: alert.reference,
        amount: null,
        currency: null,
        read: false,
        readAt: null,
        createdAt: new Date().toISOString(),
      })
    }
    alert.status = 'CONFIRMED'
    alert.reviewedAt = new Date().toISOString()
    alert.reviewedBy = 'demo-admin'
    alert.reviewNote = payload.note ?? 'Account blocked following fraud confirmation.'
    alert.timeline.push({ key: 'ACCOUNT_BLOCKED', label: 'Account blocked', description: alert.reviewNote, at: alert.reviewedAt })
    return alert
  },

  async stats(): Promise<FraudStatsSummary> {
    await delay(240, 440)
    requireAdmin()
    const rows = refresh()
    const unresolved = rows.filter((alert) => alert.status === 'OPEN' || alert.status === 'UNDER_REVIEW')
    const resolvedToday = rows.filter(
      (alert) => alert.reviewedAt && alert.reviewedAt.slice(0, 10) === new Date().toISOString().slice(0, 10),
    ).length

    const byAccount = new Map<string, { count: number; max: number }>()
    for (const alert of rows) {
      const current = byAccount.get(alert.senderAccountId) ?? { count: 0, max: 0 }
      current.count += 1
      current.max = Math.max(current.max, alert.riskScore)
      byAccount.set(alert.senderAccountId, current)
    }

    return {
      openAlerts: unresolved.filter((alert) => alert.status === 'OPEN').length + 6,
      highRisk: unresolved.filter((alert) => alert.riskLevel === 'HIGH').length,
      mediumRisk: unresolved.filter((alert) => alert.riskLevel === 'MEDIUM').length,
      lowRisk: unresolved.filter((alert) => alert.riskLevel === 'LOW').length,
      resolvedToday: resolvedToday + 24,
      confirmedToday: rows.filter((alert) => alert.status === 'CONFIRMED').length,
      totalAssessedToday: 1_842,
      averageRiskScore: Math.round(rows.reduce((sum, alert) => sum + alert.riskScore, 0) / rows.length),
      riskDistribution: {
        HIGH: rows.filter((a) => a.riskLevel === 'HIGH').length,
        MEDIUM: rows.filter((a) => a.riskLevel === 'MEDIUM').length,
        LOW: rows.filter((a) => a.riskLevel === 'LOW').length,
      },
      statusDistribution: {
        OPEN: rows.filter((a) => a.status === 'OPEN').length,
        UNDER_REVIEW: rows.filter((a) => a.status === 'UNDER_REVIEW').length,
        SAFE: rows.filter((a) => a.status === 'SAFE').length,
        CONFIRMED: rows.filter((a) => a.status === 'CONFIRMED').length,
      },
      dailyAlerts: Array.from({ length: 30 }, (_, offset) => {
        const date = new Date()
        date.setDate(date.getDate() - (29 - offset))
        return {
          label: date.toLocaleDateString('en-GB', { day: '2-digit', month: 'short' }),
          count: 24 + ((offset * 37) % 42),
        }
      }),
      topRiskyAccounts: [...byAccount.entries()]
        .map(([accountId, value]) => ({
          accountId,
          accountNumber: rows.find((a) => a.senderAccountId === accountId)?.senderAccountNumber ?? '—',
          count: value.count,
          maxRiskScore: value.max,
        }))
        .sort((a, b) => b.count - a.count)
        .slice(0, 5),
    }
  },
}