/**
 * Single entry point for the data layer.
 *
 * `VITE_USE_MOCKS=true` routes every module through `src/mock`, which implements
 * the same function signatures with realistic in-memory data. Removing mock mode
 * is therefore a single env change, and `src/mock` can be deleted without
 * touching a single view or store.
 */
import { config } from '@/api/apiClient'

import { authApi as realAuthApi } from '@/api/authApi'
import { accountApi as realAccountApi, userApi as realUserApi } from '@/api/accountApi'
import { transactionApi as realTransactionApi } from '@/api/transactionApi'
import { fraudApi as realFraudApi } from '@/api/fraudApi'
import { notificationApi as realNotificationApi } from '@/api/notificationApi'
import { adminApi as realAdminApi } from '@/api/adminApi'

import { mockAuthApi } from '@/mock/authMock'
import { mockAccountApi, mockUserApi } from '@/mock/accountMock'
import { mockTransactionApi } from '@/mock/transactionMock'
import { mockFraudApi } from '@/mock/fraudMock'
import { mockNotificationApi } from '@/mock/notificationMock'
import { mockAdminApi } from '@/mock/adminMock'

export const mockMode = config.useMocks

export const authApi = mockMode ? mockAuthApi : realAuthApi
export const accountApi = mockMode ? mockAccountApi : realAccountApi
export const userApi = mockMode ? mockUserApi : realUserApi
export const transactionApi = mockMode ? mockTransactionApi : realTransactionApi
export const fraudApi = mockMode ? mockFraudApi : realFraudApi
export const notificationApi = mockMode ? mockNotificationApi : realNotificationApi
export const adminApi = mockMode ? mockAdminApi : realAdminApi

export { configureAuthHooks, http, tokenStorage, newIdempotencyKey, config } from '@/api/apiClient'
export { AppError, humanMessageFor } from '@/utils/errors'
