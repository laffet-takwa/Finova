import type { AccountStatus, Currency, RiskLevel, TransactionStatus } from '@/types'

const CURRENCY_LOCALE: Record<Currency, string> = {
  TND: 'fr-TN',
  EUR: 'fr-FR',
  USD: 'en-US',
}

const CURRENCY_FALLBACK: Record<Currency, string> = {
  TND: 'TND',
  EUR: 'EUR',
  USD: 'USD',
}

export const CURRENCIES: Currency[] = ['TND', 'EUR', 'USD']

/** `12500.75` → `12,500.750` — three decimals, no currency symbol. */
export function formatAmount(value: number | null | undefined, decimals = 3): string {
  if (value === null || value === undefined || Number.isNaN(value)) return '—'
  return value.toLocaleString('en-US', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  })
}

/** `12500.75, 'TND'` → `12,500.750 TND`. */
export function formatMoney(
  value: number | null | undefined,
  currency: Currency | string = 'TND',
  options: { decimals?: number; withSymbol?: boolean } = {},
): string {
  const { decimals = 3, withSymbol = false } = options
  if (value === null || value === undefined || Number.isNaN(value)) return '—'
  const code = (currency as Currency) in CURRENCY_LOCALE ? (currency as Currency) : 'TND'
  const amount = value.toLocaleString('en-US', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  })
  return withSymbol ? `${amount} ${CURRENCY_FALLBACK[code]}` : `${amount} ${code}`
}

/** Signed money, for ledgers where direction matters. */
export function formatSignedMoney(
  value: number,
  currency: Currency | string = 'TND',
  options: { showPlus?: boolean } = {},
): string {
  const sign = value > 0 ? (options.showPlus === false ? '' : '+') : value < 0 ? '−' : ''
  return `${sign}${formatAmount(Math.abs(value))} ${currency}`
}

export function formatCompact(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) return '—'
  return new Intl.NumberFormat('en-US', { notation: 'compact', maximumFractionDigits: 1 }).format(value)
}

export function formatPercent(value: number | null | undefined, decimals = 1): string {
  if (value === null || value === undefined || Number.isNaN(value)) return '—'
  return `${value.toFixed(decimals)}%`
}

export function formatDate(value: string | null | undefined): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' })
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleString('en-GB', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function formatTime(value: string | null | undefined): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' })
}

export function formatRelative(value: string | null | undefined): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  const diffMs = Date.now() - date.getTime()
  const seconds = Math.round(diffMs / 1000)
  if (seconds < 45) return 'just now'
  const minutes = Math.round(seconds / 60)
  if (minutes < 60) return `${minutes} minute${minutes === 1 ? '' : 's'} ago`
  const hours = Math.round(minutes / 60)
  if (hours < 24) return `${hours} hour${hours === 1 ? '' : 's'} ago`
  const days = Math.round(hours / 24)
  if (days < 7) return `${days} day${days === 1 ? '' : 's'} ago`
  if (days < 30) return `${Math.round(days / 7)} week${Math.round(days / 7) === 1 ? '' : 's'} ago`
  return formatDate(value)
}

export function maskAccountNumber(accountNumber: string | null | undefined): string {
  if (!accountNumber) return '—'
  const clean = accountNumber.replace(/\s/g, '')
  if (clean.length <= 4) return clean
  return `•••• ${clean.slice(-4)}`
}

export function initials(firstName?: string | null, lastName?: string | null): string {
  const first = firstName?.trim()?.[0] ?? ''
  const last = lastName?.trim()?.[0] ?? ''
  return (first + last).toUpperCase() || 'FN'
}

export function fullName(person: { firstName?: string | null; lastName?: string | null } | null | undefined): string {
  if (!person) return '—'
  return `${person.firstName ?? ''} ${person.lastName ?? ''}`.trim() || '—'
}

export function greetingFor(hour = new Date().getHours()): string {
  if (hour < 12) return 'Good morning'
  if (hour < 18) return 'Good afternoon'
  return 'Good evening'
}

export const STATUS_LABELS: Record<TransactionStatus, string> = {
  PENDING: 'Pending',
  PROCESSING: 'Processing',
  COMPLETED: 'Completed',
  FAILED: 'Failed',
  REJECTED: 'Rejected',
  FLAGGED: 'Flagged',
}

export const ACCOUNT_STATUS_LABELS: Record<AccountStatus, string> = {
  ACTIVE: 'Active',
  BLOCKED: 'Blocked',
  CLOSED: 'Closed',
}

export const RISK_LABELS: Record<RiskLevel, string> = {
  LOW: 'Low risk',
  MEDIUM: 'Medium risk',
  HIGH: 'High risk',
}

export function toIsoDate(value: string | undefined | null): string | undefined {
  if (!value) return undefined
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString()
}

export function startOfDayIso(date: Date): string {
  const copy = new Date(date)
  copy.setHours(0, 0, 0, 0)
  return copy.toISOString()
}

export function endOfDayIso(date: Date): string {
  const copy = new Date(date)
  copy.setHours(23, 59, 59, 999)
  return copy.toISOString()
}

export function isValidEmail(value: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(value.trim())
}

export function isValidPhone(value: string): boolean {
  return /^[+]?[\d\s().-]{8,20}$/.test(value.trim())
}

export interface PasswordStrength {
  score: 0 | 1 | 2 | 3
  label: 'Weak' | 'Medium' | 'Strong'
  requirements: Array<{ label: string; met: boolean }>
}

export function evaluatePassword(password: string): PasswordStrength {
  const value = password ?? ''
  const requirements = [
    { label: 'At least 10 characters', met: value.length >= 10 },
    { label: 'One uppercase letter', met: /[A-Z]/.test(value) },
    { label: 'One lowercase letter', met: /[a-z]/.test(value) },
    { label: 'One number', met: /\d/.test(value) },
    { label: 'One special character', met: /[^A-Za-z0-9]/.test(value) },
  ]
  const met = requirements.filter((item) => item.met).length
  const score: 0 | 1 | 2 | 3 = met <= 2 ? 0 : met === 3 ? 1 : met === 4 ? 2 : 3
  const label: PasswordStrength['label'] = score === 0 ? 'Weak' : score === 1 ? 'Medium' : 'Strong'
  return { score, label, requirements }
}
