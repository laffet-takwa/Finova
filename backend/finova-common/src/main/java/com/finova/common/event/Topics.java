package com.finova.common.event;

/**
 * Canonical Kafka topic names.
 * <p>
 * Every topic in this class has a real producer and at least one consumer in
 * the Finova topology; none of them exist for decoration.
 */
public final class Topics {

    /** Transaction accepted and persisted as PENDING; triggers fraud analysis. */
    public static final String TRANSACTION_CREATED = "transaction.created";
    /** Transfer settled: balances debited and credited. */
    public static final String TRANSACTION_COMPLETED = "transaction.completed";
    /**
     * Fraud analysis cleared the transfer for settlement.
     * <p>
     * Deliberately distinct from {@link #TRANSACTION_COMPLETED}: this is the approval
     * decision, that one is the settlement fact. A consumer therefore never has to
     * guess which meaning a message carries, and the notification service can never
     * react to an approval as if the money had moved.
     */
    public static final String TRANSACTION_APPROVED = "transaction.approved";
    /** Transfer rejected before settlement. */
    public static final String TRANSACTION_FAILED = "transaction.failed";
    /** Fraud analysis raised the risk score and the funds are held for review. */
    public static final String TRANSACTION_FLAGGED = "transaction.flagged";
    /** A user-facing notification has been persisted. */
    public static final String NOTIFICATION_CREATED = "notification.created";
    /** An account was blocked by an administrator or by fraud review. */
    public static final String ACCOUNT_BLOCKED = "account.blocked";
    /** An account was opened (or its opening balance restated) by the account service. */
    public static final String ACCOUNT_OPENED = "account.opened";
    /** Immutable audit trail entry produced by any service. */
    public static final String AUDIT_RECORDED = "audit.recorded";

    private Topics() {
    }
}
