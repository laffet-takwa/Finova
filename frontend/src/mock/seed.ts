/**
 * Deterministic mock data layer.
 *
 * Everything here is fictional but realistic: real Tunisian names, plausible
 * merchants, internally consistent balances. Nothing is fetched from a network.
 * Switching to the real API is a single env change (`VITE_USE_MOCKS=false`) and
 * this whole directory can then be deleted without touching a single view.
 */

export const DEMO_USERS = {
  ADMIN: {
    id: 'demo-admin',
    firstName: 'Amine',
    lastName: 'Ben Salah',
    email: 'admin@finova.dev',
    phone: '+216 36 402 118',
    role: 'ADMIN' as const,
    status: 'ACTIVE' as const,
    createdAt: daysAgo(420),
  },
  TAKWA: {
    id: 'demo-takwa',
    firstName: 'Takwa',
    lastName: 'Ferchichi',
    email: 'takwa@finova.dev',
    phone: '+216 55 214 780',
    role: 'CUSTOMER' as const,
    status: 'ACTIVE' as const,
    createdAt: daysAgo(214),
  },
  INES: {
    id: 'demo-ines',
    firstName: 'Ines',
    lastName: 'Bouzid',
    email: 'ines.bouzid@finova.dev',
    phone: '+216 24 771 309',
    role: 'CUSTOMER' as const,
    status: 'ACTIVE' as const,
    createdAt: daysAgo(168),
  },
  YASSINE: {
    id: 'demo-yassine',
    firstName: 'Yassine',
    lastName: 'Trabelsi',
    email: 'yassine.trabelsi@finova.dev',
    phone: '+216 98 402 118',
    role: 'CUSTOMER' as const,
    status: 'ACTIVE' as const,
    createdAt: daysAgo(97),
  },
  SALMA: {
    id: 'demo-salma',
    firstName: 'Salma',
    lastName: 'Gharbi',
    email: 'salma.gharbi@finova.dev',
    phone: '+216 50 918 442',
    role: 'CUSTOMER' as const,
    status: 'ACTIVE' as const,
    createdAt: daysAgo(41),
  },
  SAMI: {
    id: 'demo-sami',
    firstName: 'Sami',
    lastName: 'Mejboud',
    email: 'sami.mejboud@finova.dev',
    phone: '+216 26 850 774',
    role: 'CUSTOMER' as const,
    status: 'BLOCKED' as const,
    createdAt: daysAgo(23),
  },
}

export const DEMO_PASSWORD = 'Finova#2026'

export function daysAgo(days: number, hour = 10, minute = 24): string {
  const date = new Date()
  date.setDate(date.getDate() - days)
  date.setHours(hour, minute, 0, 0)
  return date.toISOString()
}

export function hoursAgo(hours: number): string {
  return new Date(Date.now() - hours * 3600_000).toISOString()
}

export function minutesAgo(minutes: number): string {
  return new Date(Date.now() - minutes * 60_000).toISOString()
}

export function isoDayLabel(date: Date): string {
  return date.toLocaleDateString('en-GB', { day: '2-digit', month: 'short' })
}

let sequence = 0
export function nextId(prefix: string): string {
  sequence += 1
  return `${prefix}-${sequence.toString(36).padStart(4, '0')}-mock`
}

export function nextReference(date = new Date()): string {
  const stamp = date.toISOString().slice(0, 10).replace(/-/g, '')
  sequence += 1
  return `TX-${stamp}-${(sequence % 100000).toString().padStart(5, '0')}`
}

export function uuid(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID()
  return `mock-${Math.random().toString(36).slice(2, 12)}`
}

/** Simulated network latency so loading skeletons are actually exercised. */
export function delay(min = 180, max = 460): Promise<void> {
  const ms = min + Math.random() * (max - min)
  return new Promise((resolve) => setTimeout(resolve, ms))
}

export function masked(accountNumber: string): string {
  const clean = accountNumber.replace(/\s/g, '')
  return `•••• •••• ${clean.slice(-4)}`
}

export function mockError(status: number, code: string, message: string, path: string): never {
  // eslint-disable-next-line @typescript-eslint/no-throw-literal
  throw Object.assign(new Error(message), {
    name: 'AppError',
    status,
    code,
    message,
    path,
    isNetworkError: false,
  })
}