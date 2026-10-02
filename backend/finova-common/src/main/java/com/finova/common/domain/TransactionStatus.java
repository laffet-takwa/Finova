package com.finova.common.domain;

/**
 * Lifecycle of a transaction.
 * <p>
 * A transfer is created as {@code PENDING}, sent to fraud analysis, and only
 * afterwards settled as {@code COMPLETED}, rejected as {@code FAILED} or held
 * for review as {@code FLAGGED}.
 */
public enum TransactionStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    REJECTED,
    FLAGGED
}
