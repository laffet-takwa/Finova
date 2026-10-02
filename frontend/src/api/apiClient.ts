import axios, {
  AxiosError,
  type AxiosInstance,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
} from 'axios'
import { AppError, humanMessageFor } from '@/utils/errors'
import type { ApiErrorBody } from '@/types'

const ACCESS_TOKEN_KEY = 'finova.accessToken'
const REFRESH_TOKEN_KEY = 'finova.refreshToken'
const USER_KEY = 'finova.user'

export const tokenStorage = {
  getAccess: (): string | null => localStorage.getItem(ACCESS_TOKEN_KEY),
  getRefresh: (): string | null => localStorage.getItem(REFRESH_TOKEN_KEY),
  set(access: string, refresh: string): void {
    localStorage.setItem(ACCESS_TOKEN_KEY, access)
    localStorage.setItem(REFRESH_TOKEN_KEY, refresh)
  },
  setAccess(access: string): void {
    localStorage.setItem(ACCESS_TOKEN_KEY, access)
  },
  getUser<T>(): T | null {
    const raw = localStorage.getItem(USER_KEY)
    if (!raw) return null
    try {
      return JSON.parse(raw) as T
    } catch {
      return null
    }
  },
  setUser(user: unknown): void {
    localStorage.setItem(USER_KEY, JSON.stringify(user))
  },
  clear(): void {
    localStorage.removeItem(ACCESS_TOKEN_KEY)
    localStorage.removeItem(REFRESH_TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  },
}

export const config = {
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  useMocks: import.meta.env.VITE_USE_MOCKS === 'true',
  devProxyTarget: import.meta.env.VITE_DEV_PROXY_TARGET || 'http://localhost:8080',
}

type Method = 'get' | 'post' | 'put' | 'patch' | 'delete'

export interface RequestOptions {
  headers?: Record<string, string>
  params?: Record<string, unknown>
  signal?: AbortSignal
  auth?: boolean
}

function correlationId(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID()
  }
  return `web-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
}

export function newIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID()
  }
  return `idem-${Date.now()}-${Math.random().toString(36).slice(2, 11)}`
}

function toAppError(error: unknown): AppError {
  if (error instanceof AppError) return error

  if (axios.isAxiosError(error)) {
    const axiosError = error as AxiosError<ApiErrorBody>
    if (!axiosError.response) {
      return new AppError({
        status: 0,
        code: 'NETWORK_ERROR',
        message: humanMessageFor('NETWORK_ERROR'),
        isNetworkError: true,
      })
    }
    const body = axiosError.response.data
    const status = axiosError.response.status
    const code = body?.code ?? `HTTP_${status}`
    return new AppError({
      status,
      code,
      message: humanMessageFor(code, body?.message),
      correlationId: body?.correlationId ?? axiosError.response.headers['x-correlation-id'],
      details: body?.details,
    })
  }

  return new AppError({
    status: 0,
    code: 'INTERNAL_ERROR',
    message: humanMessageFor('INTERNAL_ERROR'),
  })
}

type TokenRefresher = () => Promise<string | null>
let refreshHandler: TokenRefresher | null = null
let onAuthLost: (() => void) | null = null

export function configureAuthHooks(refresh: TokenRefresher, onLost: () => void): void {
  refreshHandler = refresh
  onAuthLost = onLost
}

export const apiClient: AxiosInstance = axios.create({
  baseURL: config.baseURL,
  timeout: 20000,
  headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
})

apiClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = tokenStorage.getAccess()
  if (token && config.headers) {
    config.headers.Authorization = `Bearer ${token}`
  }
  if (config.headers) {
    config.headers['X-Correlation-Id'] = correlationId()
  }
  return config
})

let refreshInFlight: Promise<string | null> | null = null

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiErrorBody>) => {
    const original = error.config as (AxiosRequestConfig & { _retried?: boolean }) | undefined
    const status = error.response?.status
    const isAuthEndpoint = original?.url?.includes('/auth/login') ||
      original?.url?.includes('/auth/refresh') ||
      original?.url?.includes('/auth/register')

    if (status === 401 && original && !original._retried && !isAuthEndpoint && refreshHandler) {
      original._retried = true
      refreshInFlight = refreshInFlight ?? refreshHandler().finally(() => {
        refreshInFlight = null
      })
      const newToken = await refreshInFlight
      if (newToken) {
        original.headers = { ...(original.headers ?? {}), Authorization: `Bearer ${newToken}` }
        return apiClient.request(original)
      }
      tokenStorage.clear()
      onAuthLost?.()
    }

    return Promise.reject(toAppError(error))
  },
)

async function request<T>(method: Method, url: string, body?: unknown, options: RequestOptions = {}): Promise<T> {
  try {
    const response = await apiClient.request<T>({
      method,
      url,
      data: body,
      params: cleanParams(options.params),
      signal: options.signal,
      headers: options.headers,
    })
    return response.data
  } catch (error) {
    throw toAppError(error)
  }
}

function cleanParams(params?: Record<string, unknown>): Record<string, unknown> | undefined {
  if (!params) return undefined
  const entries = Object.entries(params).filter(
    ([, value]) => value !== undefined && value !== null && value !== '',
  )
  return entries.length ? Object.fromEntries(entries) : undefined
}

export const http = {
  get: <T>(url: string, options?: RequestOptions) => request<T>('get', url, undefined, options),
  post: <T>(url: string, body?: unknown, options?: RequestOptions) =>
    request<T>('post', url, body, options),
  put: <T>(url: string, body?: unknown, options?: RequestOptions) => request<T>('put', url, body, options),
  patch: <T>(url: string, body?: unknown, options?: RequestOptions) =>
    request<T>('patch', url, body, options),
  delete: <T>(url: string, options?: RequestOptions) => request<T>('delete', url, undefined, options),
}

export { toAppError }
