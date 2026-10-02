import type {
  Account,
  Notification,
  NotificationPreference,
  Transaction,
  SeriesPoint,
} from '@/types'
import { DEMO_USERS, daysAgo, hoursAgo, isoDayLabel, masked, minutesAgo, nextId } from './seed'

/**
 * In-memory datastore shared by every mock API module. It is intentionally
 * mutable so a transfer performed in the UI immediately shows up on the
 * dashboard, the account page and the notification centre.
 */
export const db = {
  accounts: [] as Account[],
  transactions: [] as Transaction[],
  notifications: [] as Notification[],
  preferences: new Map<string, NotificationPreference>(),
  preferencesLoaded: false,
}

const ACCOUNT_SEED: Account[] = [
  {
    id: 'acc-checking-6789',
    accountNumber: 'TN58 1000 0123 4567 8901 23',
    maskedAccountNumber: '•••• •••• 8901 23',
    userId: DEMO_USERS.TAKWA.id,
    accountType: 'CHECKING',
    currency: 'TND',
    balance: 12450.75,
    availableBalance: 12450.75,
    status: 'ACTIVE',
    nickname: 'Everyday Account',
    iban: 'TN591000012345678901234',
    createdAt: daysAgo(214),
    updatedAt: daysAgo(0, 8, 12),
  },
  {
    id: 'acc-savings-1234',
    accountNumber: 'TN58 1000 0123 4567 8901 34',
    maskedAccountNumber: '•••• •••• 8901 34',
    userId: DEMO_USERS.TAKWA.id,
    accountType: 'SAVINGS',
    currency: 'TND',
    balance: 5800.0,
    availableBalance: 5800.0,
    status: 'ACTIVE',
    nickname: 'Savings Account',
    iban: 'TN591000012345678901341',
    createdAt: daysAgo(198),
    updatedAt: daysAgo(3, 16, 5),
  },
  {
    id: 'acc-ines-daily',
    accountNumber: 'TN58 2000 0345 6677 8811 05',
    maskedAccountNumber: '•••• •••• 8811 05',
    userId: DEMO_USERS.INES.id,
    accountType: 'CHECKING',
    currency: 'TND',
    balance: 3240.5,
    availableBalance: 3240.5,
    status: 'ACTIVE',
    nickname: 'Daily Spending',
    iban: 'TN592000034566778811056',
    createdAt: daysAgo(168),
    updatedAt: daysAgo(1),
  },
  {
    id: 'acc-yassine-checking',
    accountNumber: 'TN58 2000 0789 1234 5678 90',
    maskedAccountNumber: '•••• •••• 5678 90',
    userId: DEMO_USERS.YASSINE.id,
    accountType: 'CHECKING',
    currency: 'TND',
    balance: 8915.0,
    availableBalance: 8915.0,
    status: 'ACTIVE',
    nickname: 'Main Account',
    iban: 'TN592000078912345678905',
    createdAt: daysAgo(97),
    updatedAt: daysAgo(2),
  },
  {
    id: 'acc-yassine-eur',
    accountNumber: 'EU76 3000 1122 3344 5566 77',
    maskedAccountNumber: '•••• •••• 5566 77',
    userId: DEMO_USERS.YASSINE.id,
    accountType: 'SAVINGS',
    currency: 'EUR',
    balance: 6400.0,
    availableBalance: 6400.0,
    status: 'ACTIVE',
    nickname: 'Euro Savings',
    iban: 'EU763000112233445566077',
    createdAt: daysAgo(95),
    updatedAt: daysAgo(6),
  },
  {
    id: 'acc-salma-savings',
    accountNumber: 'TN58 3000 0567 8899 0011 22',
    maskedAccountNumber: '•••• •••• 0011 22',
    userId: DEMO_USERS.SALMA.id,
    accountType: 'SAVINGS',
    currency: 'TND',
    balance: 15000.0,
    availableBalance: 15000.0,
    status: 'ACTIVE',
    nickname: 'Emergency Fund',
    iban: 'TN593000056788990011227',
    createdAt: daysAgo(41),
    updatedAt: daysAgo(1),
  },
  {
    id: 'acc-sami-blocked',
    accountNumber: 'TN58 3000 0900 1122 3344 55',
    maskedAccountNumber: '•••• •••• 3344 55',
    userId: DEMO_USERS.SAMI.id,
    accountType: 'CHECKING',
    currency: 'TND',
    balance: 1120.75,
    availableBalance: 0,
    status: 'BLOCKED',
    nickname: 'Everyday Account',
    iban: 'TN593000090011223344556',
    createdAt: daysAgo(23),
    updatedAt: daysAgo(2, 9, 41),
  },
]

interface TransactionSeed {
  days: number
  hour: number
  minute: number
  description: string
  amount: number
  type: Transaction['type']
  direction: 'debit' | 'credit'
  counterparty: string
  status?: Transaction['status']
  riskLevel?: Transaction['riskLevel']
  riskScore?: number
  riskReasons?: string[]
}

const TAKWA_TRANSACTIONS: TransactionSeed[] = [
  { days: 0, hour: 9, minute: 12, description: 'Netflix subscription', amount: 15.99, type: 'WITHDRAWAL', direction: 'debit', counterparty: 'Card payment' },
  { days: 0, hour: 8, minute: 2, description: 'Salary payment — Technosoft', amount: 2500, type: 'DEPOSIT', direction: 'credit', counterparty: 'Employer transfer' },
  { days: 0, hour: 7, minute: 48, description: 'Coffee Shop', amount: 8.5, type: 'WITHDRAWAL', direction: 'debit', counterparty: 'Card payment' },
  { days: 1, hour: 14, minute: 32, description: 'Transfer to Yassine Trabelsi', amount: 250, type: 'TRANSFER', direction: 'debit', counterparty: 'Yassine Trabelsi' },
  { days: 1, hour: 11, minute: 5, description: 'Electricity bill — STEG', amount: 142.3, type: 'WITHDRAWAL', direction: 'debit', counterparty: 'Bill payment' },
  { days: 2, hour: 16, minute: 20, description: 'Grocery shopping — Carrefour', amount: 86.45, type: 'WITHDRAWAL', direction: 'debit', counterparty: 'Card payment' },
  { days: 3, hour: 10, minute: 15, description: 'Rent payment', amount: 850, type: 'TRANSFER', direction: 'debit', counterparty: 'Property management' },
  { days: 4, hour: 18, minute: 40, description: 'Dinner — La Marsa', amount: 64.8, type: 'WITHDRAWAL', direction: 'debit', counterparty: 'Card payment' },
  { days: 5, hour: 9, minute: 55, description: 'Monthly salary payment', amount: 4250, type: 'DEPOSIT', direction: 'credit', counterparty: 'Employer transfer' },
  { days: 6, hour: 13, minute: 18, description: 'Fuel — Shell', amount: 92.4, type: 'WITHDRAWAL', direction: 'debit', counterparty: 'Card payment' },
  { days: 7, hour: 15, minute: 2, description: 'Family transfer', amount: 500, type: 'TRANSFER', direction: 'debit', counterparty: 'Family account' },
  { days: 9, hour: 11, minute: 30, description: 'Insurance premium — Assurances', amount: 210.0, type: 'WITHDRAWAL', direction: 'debit', counterparty: 'Bill payment' },
  { days: 11, hour: 17, minute: 22, description: 'Mobile recharge — Ooredoo', amount: 30, type: 'WITHDRAWAL', direction: 'debit', counterparty: 'Top up' },
  { days: 14, hour: 10, minute: 5, description: 'University tuition', amount: 1200, type: 'TRANSFER', direction: 'debit', counterparty: 'University fees' },
  { days: 17, hour: 19, minute: 47, description: 'Pharmacy — Pharmacie Centrale', amount: 47.9, type: 'WITHDRAWAL', direction: 'debit', counterparty: 'Card payment' },
  { days: 21, hour: 12, minute: 10, description: 'Transfer received from Ines Bouzid', amount: 320, type: 'TRANSFER', direction: 'credit', counterparty: 'Ines Bouzid' },
  { days: 26, hour: 9, minute: 33, description: 'Salary payment — Technosoft', amount: 4180, type: 'DEPOSIT', direction: 'credit', counterparty: 'Employer transfer' },
  { days: 31, hour: 14, minute: 8, description: 'Transfer to Salma Gharbi', amount: 700, type: 'TRANSFER', direction: 'debit', counterparty: 'Salma Gharbi' },
  { days: 36, hour: 16, minute: 52, description: 'Internet subscription — Tunisie Telecom', amount: 75, type: 'WITHDRAWAL', direction: 'debit', counterparty: 'Bill payment' },
  { days: 42, hour: 8, minute: 30, description: 'Monthly salary payment', amount: 4100, type: 'DEPOSIT', direction: 'credit', counterparty: 'Employer transfer' },
  { days: 8, hour: 22, minute: 14, description: 'Large transfer — hold for review', amount: 15000, type: 'TRANSFER', direction: 'debit', counterparty: 'Third party account', status: 'FLAGGED', riskLevel: 'HIGH', riskScore: 88, riskReasons: ['Large transaction amount', 'Unusual transaction pattern'] },
  { days: 12, hour: 11, minute: 41, description: 'International transfer — failed', amount: 1450, type: 'TRANSFER', direction: 'debit', counterparty: 'External beneficiary', status: 'FAILED' },
  { days: 19, hour: 15, minute: 26, description: 'Transfer to Ines Bouzid', amount: 180, type: 'TRANSFER', direction: 'debit', counterparty: 'Ines Bouzid' },
  { days: 23, hour: 10, minute: 55, description: 'Gym membership', amount: 90, type: 'WITHDRAWAL', direction: 'debit', counterparty: 'Card payment' },
]

const COUNTERPARTY_ACCOUNTS: Record<string, string> = {
  'Yassine Trabelsi': 'TN58 2000 0789 1234 5678 90',
  'Salma Gharbi': 'TN58 3000 0567 8899 0011 22',
  'Ines Bouzid': 'TN58 2000 0345 6677 8811 05',
}

const BENEFICIARY_BY_NUMBER: Record<string, string> = {
  'TN582000789123456789 0': 'Yassine Trabelsi',
  'TN5820003456677881105': 'Ines Bouzid',
  'TN5830005678899001122': 'Salma Gharbi',
  'TN58100001234567890123': 'Takwa Ferchichi',
}

function resolveBeneficiary(accountNumber: string): string {
  const clean = accountNumber.replace(/\s/g, '')
  return BENEFICIARY_BY_NUMBER[clean] ?? 'Verified Finova account'
}

function buildTransaction(seed: TransactionSeed, index: number): Transaction {
  const createdAt = daysAgo(seed.days, seed.hour, seed.minute)
  const takwaChecking = db.accounts[0]
  const isCredit = seed.direction === 'credit'
  const counterpartyAccount =
    COUNTERPARTY_ACCOUNTS[seed.counterparty] ?? 'TN58 2000 0789 1234 5678 90'
  const counterpartId = isCredit ? 'acc-external-01' : 'acc-counterparty'
  const status = seed.status ?? 'COMPLETED'

  return {
    id: `tx-${String(index + 1).padStart(4, '0')}`,
    reference: `TX-${createdAt.slice(0, 10).replace(/-/g, '')}-${(index + 1).toString().padStart(5, '0')}`,
    senderAccountId: isCredit ? counterpartId : takwaChecking.id,
    senderAccountNumber: masked(isCredit ? counterpartyAccount : takwaChecking.accountNumber),
    senderDisplay: isCredit ? seed.counterparty : takwaChecking.nickname ?? 'Everyday Account',
    receiverAccountId: isCredit ? takwaChecking.id : counterpartId,
    receiverAccountNumber: masked(isCredit ? takwaChecking.accountNumber : counterpartyAccount),
    receiverDisplay: isCredit ? takwaChecking.nickname ?? 'Everyday Account' : seed.counterparty,
    amount: seed.amount,
    currency: 'TND',
    fee: 0,
    totalAmount: seed.amount,
    description: seed.description,
    type: seed.type,
    status,
    riskScore: seed.riskScore ?? (status === 'COMPLETED' && seed.amount > 1000 ? 24 : null),
    riskLevel: seed.riskLevel ?? (status === 'COMPLETED' && seed.amount > 1000 ? 'LOW' : null),
    riskReasons: seed.riskReasons ?? null,
    failureReason: status === 'FAILED' ? 'Beneficiary account could not be credited.' : null,
    createdAt,
    completedAt: status === 'COMPLETED' ? hoursAgo(Math.max(1, seed.days * 24 - seed.hour)) : null,
    settledSenderBalance: null,
    settledReceiverBalance: null,
    correlationId: `mock-${createdAt.slice(0, 10)}-${index}`,
  }
}

function buildNotifications(): Notification[] {
  const items: Notification[] = []
  const add = (
    userId: string,
    type: Notification['type'],
    category: Notification['category'],
    severity: Notification['severity'],
    title: string,
    message: string,
    createdAt: string,
    read: boolean,
    reference?: string,
    amount?: number,
    transactionId?: string,
  ): void => {
    items.push({
      id: nextId('ntf'),
      userId,
      type,
      category,
      severity,
      title,
      message,
      transactionId: transactionId ?? null,
      reference: reference ?? null,
      amount: amount ?? null,
      currency: amount ? 'TND' : null,
      read,
      readAt: read ? createdAt : null,
      createdAt,
    })
  }

  const takwa = DEMO_USERS.TAKWA.id

  add(takwa, 'TRANSFER_COMPLETED', 'TRANSACTIONS', 'SUCCESS', 'Transfer completed', 'Your transfer of 250.000 TND to account •••• 5678 90 was successful.', minutesAgo(34), false, 'TX-20260928-00004', 250, 'tx-0004')
  add(takwa, 'TRANSFER_COMPLETED', 'TRANSACTIONS', 'SUCCESS', 'Money received', 'You received 320.000 TND from account •••• 8811 05. Reference TX-20260909-00016.', hoursAgo(9), false, 'TX-20260909-00016', 320, 'tx-0016')
  add(takwa, 'TRANSFER_FLAGGED', 'SECURITY', 'WARNING', 'Transfer held for review', 'Your transfer of 15,000.000 TND is being reviewed by our security team. Reference TX-20260922-00021.', hoursAgo(26), false, 'TX-20260922-00021', 15000, 'tx-0021')
  add(takwa, 'TRANSFER_FAILED', 'TRANSACTIONS', 'DANGER', 'Transfer failed', 'Your transfer of 1,450.000 TND could not be completed. Reference TX-20260918-00022.', daysAgo(12), true, 'TX-20260918-00022', 1450, 'tx-0022')
  add(takwa, 'SECURITY_ALERT', 'SECURITY', 'WARNING', 'New device sign-in detected', 'A sign-in from Chrome on Windows was detected for your account. If this was not you, secure your account immediately.', daysAgo(3), true)
  add(takwa, 'TRANSFER_COMPLETED', 'TRANSACTIONS', 'SUCCESS', 'Transfer completed', 'Your transfer of 700.000 TND to account •••• 0011 22 was successful.', daysAgo(31), true, 'TX-20260831-00018', 700, 'tx-0018')
  add(takwa, 'TRANSFER_COMPLETED', 'TRANSACTIONS', 'SUCCESS', 'Money received', 'You received 4,250.000 TND from your employer. Reference TX-20260925-00009.', daysAgo(5), true, 'TX-20260925-00009', 4250, 'tx-0009')
  add(takwa, 'TRANSFER_COMPLETED', 'TRANSACTIONS', 'SUCCESS', 'Transfer completed', 'Your transfer of 850.000 TND for rent was successful.', daysAgo(3), true, 'TX-20260928-00007', 850, 'tx-0007')
  add(takwa, 'TRANSFER_COMPLETED', 'TRANSACTIONS', 'SUCCESS', 'Money received', 'You received 500.000 TND from a family transfer. Reference TX-20260924-00011.', daysAgo(7), true, 'TX-20260924-00011', 500, 'tx-0011')

  add(DEMO_USERS.SAMI.id, 'ACCOUNT_BLOCKED', 'SECURITY', 'DANGER', 'Account blocked', 'Account •••• 3344 55 has been blocked. Contact support for more information.', daysAgo(2), false)
  add(DEMO_USERS.SAMI.id, 'SECURITY_ALERT', 'SECURITY', 'WARNING', 'Unusual activity detected', 'We flagged an unusual transfer pattern on your account. Our security team is reviewing it.', daysAgo(2), true)

  add(DEMO_USERS.INES.id, 'TRANSFER_COMPLETED', 'TRANSACTIONS', 'SUCCESS', 'Transfer completed', 'Your transfer of 320.000 TND to account •••• 8901 23 was successful.', daysAgo(21), true, 'TX-20260910-00016', 320)
  add(DEMO_USERS.YASSINE.id, 'TRANSFER_COMPLETED', 'TRANSACTIONS', 'SUCCESS', 'Money received', 'You received 250.000 TND from account •••• 8901 23.', daysAgo(1), true, 'TX-20260930-00004', 250)
  add(DEMO_USERS.SALMA.id, 'TRANSFER_COMPLETED', 'TRANSACTIONS', 'SUCCESS', 'Money received', 'You received 700.000 TND from account •••• 8901 23.', daysAgo(31), true, 'TX-20260831-00018', 700)
  add(DEMO_USERS.ADMIN.id, 'SECURITY_ALERT', 'SYSTEM', 'INFO', 'Daily audit summary ready', 'The audit trail for the last 24 hours is available in the audit log viewer.', hoursAgo(5), true)

  return items.sort((a, b) => b.createdAt.localeCompare(a.createdAt))
}

export function initialise(): void {
  if (db.accounts.length > 0) return
  db.accounts = ACCOUNT_SEED.map((account) => ({ ...account }))
  db.transactions = TAKWA_TRANSACTIONS.map((seed, index) => buildTransaction(seed, index))
  db.notifications = buildNotifications()

  if (!db.preferencesLoaded) {
    const defaults: NotificationPreference = {
      emailEnabled: true,
      pushEnabled: true,
      inAppEnabled: true,
      transferAlerts: true,
      securityAlerts: true,
      marketingEmails: false,
    }
    for (const user of Object.values(DEMO_USERS)) db.preferences.set(user.id, { ...defaults })
    db.preferencesLoaded = true
  }
}

export function accountByNumber(accountNumber: string): Account | undefined {
  const clean = accountNumber.replace(/\s/g, '').toUpperCase()
  return db.accounts.find((account) => account.accountNumber.replace(/\s/g, '').toUpperCase() === clean)
}

export function beneficiaryName(accountNumber: string): string {
  return resolveBeneficiary(accountNumber)
}

export function dailySeries(days: number): SeriesPoint[] {
  const points: SeriesPoint[] = []
  for (let offset = days - 1; offset >= 0; offset -= 1) {
    const date = new Date()
    date.setDate(date.getDate() - offset)
    points.push({ label: isoDayLabel(date), count: 0, total: 0 })
  }
  return points
}

initialise()