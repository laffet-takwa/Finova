package com.finova.common.audit;

/** Auditable actions recorded by the platform. */
public enum AuditAction {
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    LOGOUT,
    TOKEN_REFRESHED,
    USER_REGISTERED,
    PROFILE_UPDATED,
    ACCOUNT_CREATED,
    ACCOUNT_BLOCKED,
    ACCOUNT_STATUS_CHANGED,
    TRANSFER_CREATED,
    TRANSFER_COMPLETED,
    TRANSFER_FAILED,
    FRAUD_DETECTED,
    FRAUD_REVIEWED,
    NOTIFICATION_READ
}
