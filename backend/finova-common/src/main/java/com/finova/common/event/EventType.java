package com.finova.common.event;

/** Logical event names carried on the {@link DomainEvent} envelope. */
public enum EventType {
    TRANSACTION_CREATED,
    TRANSACTION_APPROVED,
    TRANSACTION_COMPLETED,
    TRANSACTION_FAILED,
    TRANSACTION_FLAGGED,
    NOTIFICATION_REQUESTED,
    NOTIFICATION_CREATED,
    ACCOUNT_BLOCKED,
    ACCOUNT_OPENED,
    AUDIT_RECORDED
}
