import { AppError } from '@/utils/errors'
import { db } from './database'
import { delay, nextReference } from './seed'
import type {
  AdminTransactionStats,
  CreateTransactionRequest,
  PageResponse,
  Transaction,
  TransactionFilters,
  TransactionSummary,
  TransactionTimeline,
  TimelineStep,
} from '@/types'

function fail(status: number, code: string, message: string): never {
  throw new AppError({ status, code, message })
}

function currentUserId(): string {
  return localStorage.getItem('finova.mock.activeUserId') ?? 'demo-takwa'
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

function matches(transaction: Transaction, filters: TransactionFilters): boolean {
  if (filters.status && transaction.status !== filters.status) return false
  if (filters.type && transaction.type !== filters.type) return false
  if (filters.currency && transaction.currency !== filters.currency) return false
  if (filters.accountId) {
    const own = db.accounts.find((account) => account.id === filters.accountId)
    if (own && transaction.senderAccountId !== own.id && transaction.receiverAccountId !== own.id) {
      return false
    }
  }
  if (filters.minAmount !== undefined && transaction.amount < filters.minAmount) return false
  if (filters.maxAmount !== undefined && transaction.amount > filters.maxAmount) return false
  if (filters.search) {
    const needle = filters.search.toLowerCase()
    const haystack = `${transaction.description ?? ''} ${transaction.reference}`.toLowerCase()
    if (!haystack.includes(needle)) return false
  }
  if (filters.from && transaction.createdAt < new Date(filters.from).toISOString()) return false
  if (filters.to) {
    const end = new Date(filters.to)
    end.setHours(23, 59, 59, 999)
    if (transaction.createdAt > end.toISOString()) return false
  }
  return true
}

function buildTimeline(transaction: Transaction): TransactionTimeline {
  const steps: TimelineStep[] = [
    {
      key: 'CREATED',
      label: 'Transfer created',
      description: 'The transfer was accepted and recorded as pending.',
      state: 'DONE',
      at: transaction.createdAt,
    },
    {
      key: 'VALIDATED',
      label: 'Account validation',
      description:
        transaction.status === 'FAILED'
          ? 'Validation failed — the transfer could not proceed.'
          : 'Sender ownership, balance, currency and account status were verified.',
      state: transaction.status === 'FAILED' ? 'FAILED' : 'DONE',
      at: transaction.completedAt ?? transaction.createdAt,
    },
    {
      key: 'FRAUD_CHECKED',
      label: 'Fraud analysis',
      description: transaction.riskScore
        ? `Risk scored ${transaction.riskScore}/100 (${transaction.riskLevel ?? 'UNKNOWN'}).`
        : 'Fraud analysis has not reported a score for this transfer yet.',
      state: transaction.riskScore ? 'DONE' : 'PENDING',
      at: transaction.riskScore ? transaction.completedAt ?? transaction.createdAt : null,
    },
    {
      key: 'SETTLED',
      label:
        transaction.status === 'FLAGGED'
          ? 'Held for review'
          : transaction.status === 'FAILED'
            ? 'Not settled'
            : 'Balances updated',
      description:
        transaction.status === 'FLAGGED'
          ? 'Funds are held while the security team reviews this transfer.'
          : transaction.status === 'FAILED'
            ? 'No money was moved.'
            : 'The sender was debited and the receiver was credited.',
      state:
        transaction.status === 'COMPLETED'
          ? 'DONE'
          : transaction.status === 'FAILED'
            ? 'FAILED'
            : 'CURRENT',
      at: transaction.completedAt,
    },
    {
      key: 'NOTIFIED',
      label: 'Notification sent',
      description:
        'The notification service delivers the outcome to your notification centre.',
      state: transaction.status === 'PENDING' ? 'CURRENT' : 'PENDING',
      at: null,
    },
  ]

  return { transactionId: transaction.id, reference: transaction.reference, steps }
}

export const mockTransactionApi = {
  async create(
    payload: CreateTransactionRequest,
    options?: { idempotencyKey?: string },
  ): Promise<{ transaction: Transaction; idempotentReplay: boolean }> {
    await delay(520, 900)

    const key = options?.idempotencyKey ?? ''
    const fingerprint = JSON.stringify(payload)
    const replay = db.transactions.find((item) => item.reference === key)
    if (replay) {
      return { transaction: replay, idempotentReplay: true }
    }

    const sender = db.accounts.find((account) => account.id === payload.senderAccountId)
    if (!sender) fail(404, 'ACCOUNT_NOT_FOUND', 'The selected account was not found.')
    if (sender.userId !== currentUserId()) {
      fail(403, 'ACCESS_DENIED', 'You do not have access to this resource.')
    }

    const receiver = db.accounts.find(
      (account) => account.accountNumber.replace(/\s/g, '') === payload.receiverAccountNumber.replace(/\s/g, ''),
    )
    if (!receiver) fail(404, 'ACCOUNT_NOT_FOUND', 'The selected account was not found.')

    if (sender.status !== 'ACTIVE' || receiver.status !== 'ACTIVE') {
      fail(422, 'ACCOUNT_NOT_ACTIVE', 'One of the accounts is not active.')
    }
    if (sender.id === receiver.id) {
      fail(422, 'SENDER_RECEIVER_IDENTICAL', 'The source and destination accounts must be different.')
    }
    if (payload.amount <= 0) fail(400, 'VALIDATION_ERROR', 'Amount must be greater than zero.')
    if (payload.amount < 0.001) fail(422, 'AMOUNT_BELOW_MINIMUM', 'The amount is below the minimum transfer value.')
    if (payload.amount > 1_000_000) fail(422, 'AMOUNT_ABOVE_MAXIMUM', 'The amount exceeds the maximum transfer value.')
    if (sender.balance < payload.amount) {
      fail(422, 'INSUFFICIENT_BALANCE', 'Insufficient balance for this transfer.')
    }
    if (sender.currency !== receiver.currency || sender.currency !== payload.currency) {
      fail(422, 'SAME_CURRENCY_REQUIRED', 'Transfers are only possible between accounts of the same currency.')
    }

    void fingerprint
    const createdAt = new Date().toISOString()
    const flagged = payload.amount > 10_000
    const transaction: Transaction = {
      id: `tx-${Date.now().toString(36)}`,
      reference: nextReference(),
      senderAccountId: sender.id,
      senderAccountNumber: sender.maskedAccountNumber,
      senderDisplay: sender.nickname ?? 'Everyday Account',
      receiverAccountId: receiver.id,
      receiverAccountNumber: receiver.maskedAccountNumber,
      receiverDisplay: receiver.nickname ?? 'Beneficiary account',
      amount: Number(payload.amount.toFixed(3)),
      currency: payload.currency,
      fee: 0,
      totalAmount: Number(payload.amount.toFixed(3)),
      description: payload.description?.trim() || null,
      type: 'TRANSFER',
      status: flagged ? 'FLAGGED' : 'COMPLETED',
      riskScore: flagged ? 88 : 12,
      riskLevel: flagged ? 'HIGH' : 'LOW',
      riskReasons: flagged
        ? ['Large transaction amount', 'Unusual transaction pattern']
        : ['Within normal account behaviour'],
      failureReason: null,
      createdAt,
      completedAt: flagged ? null : createdAt,
      settledSenderBalance: flagged ? null : Number((sender.balance - payload.amount).toFixed(3)),
      settledReceiverBalance: flagged ? null : Number((receiver.balance + payload.amount).toFixed(3)),
      correlationId: `mock-${Date.now().toString(36)}`,
    }

    if (!flagged) {
      sender.balance = Number((sender.balance - payload.amount).toFixed(3))
      sender.availableBalance = sender.balance
      sender.updatedAt = createdAt
      receiver.balance = Number((receiver.balance + payload.amount).toFixed(3))
      receiver.availableBalance = receiver.balance
      receiver.updatedAt = createdAt

      db.notifications.unshift(
        {
          id: `ntf-${Date.now().toString(36)}-a`,
          userId: sender.userId,
          type: 'TRANSFER_COMPLETED',
          category: 'TRANSACTIONS',
          severity: 'SUCCESS',
          title: 'Transfer completed',
          message: `Your transfer of ${payload.amount.toFixed(3)} ${payload.currency} to account ${receiver.maskedAccountNumber} was successful.`,
          transactionId: transaction.id,
          reference: transaction.reference,
          amount: transaction.amount,
          currency: transaction.currency,
          read: false,
          readAt: null,
          createdAt,
        },
        {
          id: `ntf-${Date.now().toString(36)}-b`,
          userId: receiver.userId,
          type: 'TRANSFER_COMPLETED',
          category: 'TRANSACTIONS',
          severity: 'SUCCESS',
          title: 'Money received',
          message: `You received ${payload.amount.toFixed(3)} ${payload.currency} from account ${sender.maskedAccountNumber}. Reference ${transaction.reference}.`,
          transactionId: transaction.id,
          reference: transaction.reference,
          amount: transaction.amount,
          currency: transaction.currency,
          read: false,
          readAt: null,
          createdAt,
        },
      )
    } else {
      db.notifications.unshift({
        id: `ntf-${Date.now().toString(36)}-c`,
        userId: sender.userId,
        type: 'TRANSFER_FLAGGED',
        category: 'SECURITY',
        severity: 'WARNING',
        title: 'Transfer held for review',
        message: `Your transfer of ${payload.amount.toFixed(3)} ${payload.currency} is being reviewed by our security team. Reference ${transaction.reference}.`,
        transactionId: transaction.id,
        reference: transaction.reference,
        amount: transaction.amount,
        currency: transaction.currency,
        read: false,
        readAt: null,
        createdAt,
      })
    }

    db.transactions.unshift(transaction)
    return { transaction, idempotentReplay: false }
  },

  async list(filters: TransactionFilters): Promise<PageResponse<Transaction>> {
    await delay(260, 500)
    const rows = db.transactions.filter((transaction) => matches(transaction, filters))
    return paginate(rows, filters.page ?? 0, filters.size ?? 20)
  },

  async get(id: string): Promise<Transaction> {
    await delay(200, 380)
    const transaction = db.transactions.find((item) => item.id === id)
    if (!transaction) fail(404, 'TRANSACTION_NOT_FOUND', 'The selected transaction was not found.')
    return transaction
  },

  async timeline(id: string): Promise<TransactionTimeline> {
    await delay(200, 360)
    const transaction = await this.get(id)
    return buildTimeline(transaction)
  },

  async summary(): Promise<TransactionSummary> {
    await delay(240, 440)
    const completed = db.transactions.filter((transaction) => transaction.status === 'COMPLETED')
    const income = completed
      .filter((transaction) => transaction.receiverAccountId === 'acc-checking-6789')
      .reduce((sum, transaction) => sum + transaction.amount, 0)
    const expenses = completed
      .filter((transaction) => transaction.senderAccountId === 'acc-checking-6789')
      .reduce((sum, transaction) => sum + transaction.amount, 0)

    const dailySeries = Array.from({ length: 30 }, (_, offset) => {
      const date = new Date()
      date.setDate(date.getDate() - (29 - offset))
      const day = date.toISOString().slice(0, 10)
      const sameDay = completed.filter((transaction) => transaction.createdAt.slice(0, 10) === day)
      return {
        label: date.toLocaleDateString('en-GB', { day: '2-digit', month: 'short' }),
        income: sameDay
          .filter((t) => t.receiverAccountId === 'acc-checking-6789')
          .reduce((sum, t) => sum + t.amount, 0),
        expenses: sameDay
          .filter((t) => t.senderAccountId === 'acc-checking-6789')
          .reduce((sum, t) => sum + t.amount, 0),
        count: sameDay.length,
      }
    })

    return {
      income: Number(income.toFixed(3)),
      expenses: Number(expenses.toFixed(3)),
      transactionCount: db.transactions.length,
      monthChangePercent: 4.8,
      monthChangeAbsolute: 580.4,
      currency: 'TND',
      dailySeries,
      recentTransactions: db.transactions.slice(0, 6),
    }
  },

  async adminStats(): Promise<AdminTransactionStats> {
    await delay(240, 440)
    return {
      totalTransactions: 486_320,
      completedToday: 8421,
      failedToday: 137,
      pendingCount: db.transactions.filter((t) => t.status === 'PENDING').length + 23,
      flaggedCount: db.transactions.filter((t) => t.status === 'FLAGGED').length + 37,
      successRate: 98.4,
      volumeToday: 2_412_860,
      volumeByCurrency: { TND: 2_054_300, EUR: 241_800, USD: 116_760 },
      statusDistribution: { COMPLETED: 8421, FAILED: 137, FLAGGED: 37, PENDING: 23 },
      typeDistribution: { TRANSFER: 7_104, DEPOSIT: 1_098, WITHDRAWAL: 1_416 },
      hourlyVolume: Array.from({ length: 24 }, (_, hour) => ({
        label: `${String(hour).padStart(2, '0')}:00`,
        count: hour < 7 ? 40 + ((hour * 13) % 30) : hour < 19 ? 320 + ((hour * 47) % 180) : 90 + ((hour * 17) % 60),
      })),
      dailyVolume: Array.from({ length: 30 }, (_, offset) => {
        const date = new Date()
        date.setDate(date.getDate() - (29 - offset))
        return {
          label: date.toLocaleDateString('en-GB', { day: '2-digit', month: 'short' }),
          count: 7_800 + ((offset * 613) % 2_400),
        }
      }),
      averageAmount: 286.42,
      largestAmount: 15_000,
    }
  },

  async adminList(filters: TransactionFilters): Promise<PageResponse<Transaction>> {
    await delay(260, 480)
    const rows = db.transactions.filter((transaction) => matches(transaction, filters))
    return paginate(rows, filters.page ?? 0, filters.size ?? 20)
  },
}