import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi, mockMode, tokenStorage } from '@/api'
import { AppError } from '@/utils/errors'
import type { AuthUser, LoginRequest, RegisterRequest, Role } from '@/types'

const MOCK_USERS_KEY = 'finova.mock.activeUserId'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<AuthUser | null>(tokenStorage.getUser<AuthUser>())
  const accessToken = ref<string | null>(tokenStorage.getAccess())
  const refreshToken = ref<string | null>(tokenStorage.getRefresh())
  const loading = ref(false)
  const initialized = ref(false)

  const isAuthenticated = computed(() => Boolean(accessToken.value && user.value))
  const role = computed<Role | null>(() => user.value?.role ?? null)
  const isAdmin = computed(() => role.value === 'ADMIN')
  const isCustomer = computed(() => role.value === 'CUSTOMER')
  const fullName = computed(() =>
    user.value ? `${user.value.firstName} ${user.value.lastName}`.trim() : '',
  )
  const initials = computed(() => {
    if (!user.value) return 'FN'
    return `${user.value.firstName?.[0] ?? ''}${user.value.lastName?.[0] ?? ''}`.toUpperCase()
  })

  function applySession(next: { accessToken: string; refreshToken: string; user: AuthUser }): void {
    accessToken.value = next.accessToken
    refreshToken.value = next.refreshToken
    user.value = next.user
    tokenStorage.set(next.accessToken, next.refreshToken)
    tokenStorage.setUser(next.user)
  }

  function clearSession(): void {
    accessToken.value = null
    refreshToken.value = null
    user.value = null
    tokenStorage.clear()
  }

  async function login(credentials: LoginRequest): Promise<AuthUser> {
    loading.value = true
    try {
      const response = await authApi.login(credentials)
      applySession(response)
      if (mockMode) localStorage.setItem(MOCK_USERS_KEY, response.user.id)
      return response.user
    } finally {
      loading.value = false
    }
  }

  async function register(payload: RegisterRequest): Promise<AuthUser> {
    loading.value = true
    try {
      const response = await authApi.register(payload)
      applySession(response)
      if (mockMode) localStorage.setItem(MOCK_USERS_KEY, response.user.id)
      return response.user
    } finally {
      loading.value = false
    }
  }

  async function logout(): Promise<void> {
    const token = refreshToken.value
    clearSession()
    if (!token) return
    try {
      await authApi.logout(token)
    } catch (error) {
      if (!(error instanceof AppError)) throw error
    }
  }

  /** Silent refresh used by the axios interceptor. */
  async function refreshSession(): Promise<string | null> {
    const token = refreshToken.value
    if (!token) return null
    try {
      const response = await authApi.refresh(token)
      applySession(response)
      return response.accessToken
    } catch {
      clearSession()
      return null
    }
  }

  async function fetchMe(): Promise<AuthUser | null> {
    if (!isAuthenticated.value) return null
    try {
      const me = await (await import('@/api')).userApi.me()
      user.value = me
      tokenStorage.setUser(me)
      return me
    } catch {
      return null
    }
  }

  function updateLocalUser(patch: Partial<AuthUser>): void {
    if (!user.value) return
    user.value = { ...user.value, ...patch }
    tokenStorage.setUser(user.value)
  }

  function initialize(): void {
    if (initialized.value) return
    const storedUser = tokenStorage.getUser<AuthUser>()
    const storedAccess = tokenStorage.getAccess()
    if (storedUser && storedAccess) {
      user.value = storedUser
      accessToken.value = storedAccess
    }
    initialized.value = true
  }

  return {
    user,
    accessToken,
    refreshToken,
    loading,
    initialized,
    isAuthenticated,
    role,
    isAdmin,
    isCustomer,
    fullName,
    initials,
    login,
    register,
    logout,
    refreshSession,
    fetchMe,
    updateLocalUser,
    initialize,
    clearSession,
  }
})
