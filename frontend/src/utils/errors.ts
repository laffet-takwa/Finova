/**
 * Normalised application error. Every failed request resolves to one of these,
 * so views never have to interpret an Axios error.
 */
export class AppError extends Error {
  readonly status: number
  readonly code: string
  readonly correlationId?: string
  readonly details?: Record<string, string>
  readonly isNetworkError: boolean

  constructor(init: {
    status: number
    code: string
    message: string
    correlationId?: string
    details?: Record<string, string>
    isNetworkError?: boolean
  }) {
    super(init.message)
    this.name = 'AppError'
    this.status = init.status
    this.code = init.code
    this.correlationId = init.correlationId
    this.details = init.details
    this.isNetworkError = init.isNetworkError ?? false
  }

  get isAuthError(): boolean {
    return this.status === 401
  }

  get isForbidden(): boolean {
    return this.status === 403
  }

  fieldError(field: string): string | undefined {
    return this.details?.[field]
  }
}

export const ERROR_MESSAGES: Record<string, string> = {
  VALIDATION_ERROR: 'Please check the highlighted fields and try again.',
  MALFORMED_REQUEST: 'The request could not be processed.',
  INVALID_CREDENTIALS: 'Invalid email or password.',
  ACCOUNT_LOCKED: 'This account has been blocked. Contact Finova support.',
  ACCESS_DENIED: 'You do not have access to this resource.',
  UNAUTHENTICATED: 'Your session has expired. Please sign in again.',
  TOKEN_EXPIRED: 'Your session has expired. Please sign in again.',
  TOKEN_INVALID: 'Your session is no longer valid. Please sign in again.',
  EMAIL_ALREADY_EXISTS: 'An account already exists for this email address.',
  DUPLICATE_RESOURCE: 'That record already exists.',
  INSUFFICIENT_BALANCE: 'Insufficient balance for this transfer.',
  ACCOUNT_NOT_ACTIVE: 'One of the accounts is not active.',
  ACCOUNT_NOT_FOUND: 'The selected account was not found.',
  TRANSACTION_NOT_FOUND: 'The selected transaction was not found.',
  USER_NOT_FOUND: 'The selected user was not found.',
  NOTIFICATION_NOT_FOUND: 'The selected notification was not found.',
  FRAUD_ALERT_NOT_FOUND: 'The selected fraud alert was not found.',
  ALERT_ALREADY_REVIEWED: 'This alert has already been reviewed.',
  SENDER_RECEIVER_IDENTICAL: 'The source and destination accounts must be different.',
  CURRENCY_NOT_SUPPORTED: 'That currency is not supported.',
  AMOUNT_BELOW_MINIMUM: 'The amount is below the minimum transfer value.',
  AMOUNT_ABOVE_MAXIMUM: 'The amount exceeds the maximum transfer value.',
  SAME_CURRENCY_REQUIRED: 'Transfers are only possible between accounts of the same currency.',
  ACCOUNT_LIMIT_REACHED: 'You have reached the maximum number of accounts.',
  IDEMPOTENCY_KEY_REUSED: 'This transfer request was already submitted with different details.',
  OPERATION_NOT_ALLOWED: 'This action is not available for the current status.',
  RATE_LIMIT_EXCEEDED: 'Too many requests. Please wait a moment and try again.',
  SERVICE_UNAVAILABLE: 'Finova is temporarily unavailable. Please retry shortly.',
  INTERNAL_ERROR: 'Something went wrong on our side. Please try again.',
  NETWORK_ERROR: 'You appear to be offline. Check your connection and try again.',
}

export function humanMessageFor(code: string | undefined, fallback?: string): string {
  if (!code) return fallback ?? ERROR_MESSAGES.INTERNAL_ERROR
  return ERROR_MESSAGES[code] ?? fallback ?? ERROR_MESSAGES.INTERNAL_ERROR
}
