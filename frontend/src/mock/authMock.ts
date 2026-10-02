import { AppError } from '@/utils/errors'
import { DEMO_PASSWORD, DEMO_USERS, delay } from './seed'
import type { AuthResponse, AuthUser, LoginRequest, RegisterRequest } from '@/types'

const SESSION_KEY = 'finova.mock.session'
const USERS_KEY = 'finova.mock.users'

interface MockSession {
  userId: string
  issuedAt: number
}

interface StoredUser {
  id: string
  firstName: string
  lastName: string
  email: string
  phone: string
  passwordHash: string
  role: 'CUSTOMER' | 'ADMIN'
  status: 'ACTIVE' | 'BLOCKED'
  createdAt: string
}

function loadUsers(): StoredUser[] {
  const raw = localStorage.getItem(USERS_KEY)
  if (raw) return JSON.parse(raw) as StoredUser[]
  const seeded: StoredUser[] = Object.values(DEMO_USERS).map((user) => ({
    ...user,
    passwordHash: DEMO_PASSWORD,
  }))
  localStorage.setItem(USERS_KEY, JSON.stringify(seeded))
  return seeded
}

function persistUsers(users: StoredUser[]): void {
  localStorage.setItem(USERS_KEY, JSON.stringify(users))
}

/** Mirrors the real BCrypt-backed flow: the plaintext password is never stored. */
function hashPassword(password: string): string {
  let hash = 0x811c9dc5
  for (let index = 0; index < password.length; index += 1) {
    hash ^= password.charCodeAt(index)
    hash = Math.imul(hash, 0x01000193) >>> 0
  }
  return `mock-bcrypt$${hash.toString(16).padStart(8, '0')}`
}

function verifyPassword(password: string, stored: string): boolean {
  return stored === hashPassword(password)
}

function fail(status: number, code: string, message: string): never {
  throw new AppError({ status, code, message })
}

function toAuthUser(user: StoredUser): AuthUser {
  return {
    id: user.id,
    firstName: user.firstName,
    lastName: user.lastName,
    email: user.email,
    phone: user.phone,
    role: user.role,
    status: user.status,
    createdAt: user.createdAt,
  }
}

function issue(user: StoredUser): AuthResponse {
  const authUser = toAuthUser(user)
  const session: MockSession = { userId: user.id, issuedAt: Date.now() }
  localStorage.setItem(SESSION_KEY, JSON.stringify(session))
  localStorage.setItem('finova.accessToken', `mock.${btoa(`${user.id}:${user.role}:${Date.now()}`)}.access`)
  localStorage.setItem('finova.refreshToken', `mock.${btoa(`${user.id}:refresh:${Date.now()}`)}.refresh`)
  localStorage.setItem('finova.user', JSON.stringify(authUser))
  return {
    accessToken: localStorage.getItem('finova.accessToken') as string,
    refreshToken: localStorage.getItem('finova.refreshToken') as string,
    expiresIn: 3600,
    tokenType: 'Bearer',
    user: authUser,
  }
}

function currentUser(): StoredUser {
  const raw = localStorage.getItem(SESSION_KEY)
  if (!raw) fail(401, 'UNAUTHENTICATED', 'Your session has expired. Please sign in again.')
  const session = JSON.parse(raw) as MockSession
  const user = loadUsers().find((item) => item.id === session.userId)
  if (!user) fail(401, 'TOKEN_INVALID', 'The provided token is not valid.')
  return user
}

export const mockAuthApi = {
  async register(payload: RegisterRequest): Promise<AuthResponse> {
    await delay(320, 620)
    const users = loadUsers()
    const email = payload.email.trim().toLowerCase()

    if (users.some((user) => user.email === email)) {
      fail(409, 'EMAIL_ALREADY_EXISTS', 'An account already exists for this email address.')
    }
    if (payload.password !== payload.confirmPassword) {
      fail(400, 'VALIDATION_ERROR', 'Password confirmation does not match.')
    }

    const created: StoredUser = {
      id: `demo-user-${Date.now().toString(36)}`,
      firstName: payload.firstName.trim(),
      lastName: payload.lastName.trim(),
      email,
      phone: payload.phone.trim(),
      passwordHash: hashPassword(payload.password),
      role: 'CUSTOMER',
      status: 'ACTIVE',
      createdAt: new Date().toISOString(),
    }

    persistUsers([...users, created])
    return issue(created)
  },

  async login(payload: LoginRequest): Promise<AuthResponse> {
    await delay(280, 560)
    const email = payload.email.trim().toLowerCase()
    const user = loadUsers().find((item) => item.email === email)

    if (!user || !verifyPassword(payload.password, user.passwordHash)) {
      fail(401, 'INVALID_CREDENTIALS', 'Invalid email or password.')
    }
    if (user.status === 'BLOCKED') {
      fail(403, 'ACCOUNT_LOCKED', 'This account has been blocked. Contact Finova support.')
    }
    return issue(user)
  },

  async refresh(refreshToken: string): Promise<AuthResponse> {
    await delay(120, 260)
    if (!refreshToken || !refreshToken.includes('refresh')) {
      fail(401, 'TOKEN_INVALID', 'The provided token is not valid.')
    }
    return issue(currentUser())
  },

  async logout(refreshToken: string): Promise<void> {
    await delay(80, 180)
    void refreshToken
    localStorage.removeItem(SESSION_KEY)
  },

  me(): Promise<AuthUser> {
    return Promise.resolve(toAuthUser(currentUser()))
  },
}

export { currentUser as mockCurrentUser, loadUsers as mockLoadUsers, persistUsers as mockPersistUsers }