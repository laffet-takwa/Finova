package com.finova.fraud.event;

/**
 * Topics and logical event names the fraud service needs but {@code finova-common} does not declare
 * yet.
 * <p>
 * They are kept local, in one place, so that promoting them into
 * {@code com.finova.common.event.Topics} / {@code EventType} is a one-line change here and a
 * one-line change in the producing service.
 */
public final class FraudTopics {

    /**
     * Fraud analysis cleared the transfer. The transaction service settles the held funds.
     * Pending addition to {@code com.finova.common.event.Topics}:
     * {@code public static final String TRANSACTION_APPROVED = "transaction.approved";}
     */
    public static final String TRANSACTION_APPROVED = "transaction.approved";

    /**
     * Pending addition to {@code com.finova.common.event.EventType}: a new enum constant
     * {@code TRANSACTION_APPROVED}. Until it exists the envelope carries the name as a string.
     */
    public static final String TRANSACTION_APPROVED_TYPE = "TRANSACTION_APPROVED";

    /** Logical event name of a funds release ordered by an administrator. */
    public static final String TRANSACTION_RELEASED_TYPE = "TRANSACTION_APPROVED";

    private FraudTopics() {
    }
}
