package com.finova.fraud.domain;

/** Administrator actions on a fraud alert, as recorded in the audit trail. */
public enum FraudAction {

    REVIEW_STARTED,
    MARKED_SAFE,
    CONFIRMED_FRAUD,
    ACCOUNT_BLOCKED
}
