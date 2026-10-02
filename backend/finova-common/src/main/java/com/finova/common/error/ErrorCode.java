package com.finova.common.error;

/**
 * Canonical business error codes returned by every Finova service.
 * The HTTP status is derived from the code so error handling stays consistent
 * across the whole platform.
 */
public enum ErrorCode {

    VALIDATION_ERROR(400, "Request validation failed."),
    MALFORMED_REQUEST(400, "The request could not be processed."),
    INVALID_CREDENTIALS(401, "Invalid email or password."),
    ACCOUNT_LOCKED(403, "This account has been blocked. Contact support."),
    ACCESS_DENIED(403, "You are not allowed to perform this operation."),
    UNAUTHENTICATED(401, "Authentication is required to access this resource."),
    TOKEN_EXPIRED(401, "Your session has expired. Please sign in again."),
    TOKEN_INVALID(401, "The provided token is not valid."),
    EMAIL_ALREADY_EXISTS(409, "An account already exists for this email address."),
    DUPLICATE_RESOURCE(409, "The resource already exists."),
    INSUFFICIENT_BALANCE(422, "Insufficient balance for this transaction."),
    ACCOUNT_NOT_ACTIVE(422, "The account is not active."),
    ACCOUNT_NOT_FOUND(404, "The requested account was not found."),
    TRANSACTION_NOT_FOUND(404, "The requested transaction was not found."),
    USER_NOT_FOUND(404, "The requested user was not found."),
    NOTIFICATION_NOT_FOUND(404, "The requested notification was not found."),
    FRAUD_ALERT_NOT_FOUND(404, "The requested fraud alert was not found."),
    ALERT_ALREADY_REVIEWED(409, "This fraud alert has already been reviewed."),
    SENDER_RECEIVER_IDENTICAL(422, "The source and destination accounts must be different."),
    CURRENCY_NOT_SUPPORTED(422, "The requested currency is not supported."),
    AMOUNT_BELOW_MINIMUM(422, "The amount is below the minimum allowed transfer value."),
    AMOUNT_ABOVE_MAXIMUM(422, "The amount exceeds the maximum allowed transfer value."),
    SAME_CURRENCY_REQUIRED(422, "Transfers are only allowed between accounts of the same currency."),
    ACCOUNT_LIMIT_REACHED(422, "The maximum number of accounts has been reached."),
    IDEMPOTENCY_KEY_REUSED(409, "This idempotency key was already used with a different request payload."),
    OPERATION_NOT_ALLOWED(405, "This operation is not allowed for the current resource state."),
    RATE_LIMIT_EXCEEDED(429, "Too many requests. Please slow down and try again."),
    RESOURCE_NOT_FOUND(404, "The requested resource was not found."),
    GATEWAY_ERROR(502, "The upstream service could not be reached."),
    SERVICE_UNAVAILABLE(503, "The service is temporarily unavailable. Please retry shortly."),
    INTERNAL_ERROR(500, "An unexpected error occurred.");

    private final int httpStatus;
    private final String defaultMessage;

    ErrorCode(int httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
