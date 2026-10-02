import { http, type RequestOptions } from '@/api/apiClient'
import type { AuthResponse, LoginRequest, RegisterRequest } from '@/types'

export const authApi = {
  register: (payload: RegisterRequest, options?: RequestOptions) =>
    http.post<AuthResponse>('/auth/register', payload, options),

  login: (payload: LoginRequest, options?: RequestOptions) =>
    http.post<AuthResponse>('/auth/login', payload, options),

  refresh: (refreshToken: string, options?: RequestOptions) =>
    http.post<AuthResponse>('/auth/refresh', { refreshToken }, options),

  logout: (refreshToken: string, options?: RequestOptions) =>
    http.post<void>('/auth/logout', { refreshToken }, options),
}
