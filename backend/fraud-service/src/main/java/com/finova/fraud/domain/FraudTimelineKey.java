package com.finova.fraud.domain;

/** Canonical timeline step keys so the UI and the audit trail share one vocabulary. */
public enum FraudTimelineKey {

    TRANSACTION_CREATED("Transaction created",
            "The transfer was accepted by the transaction service and queued for analysis."),
    AMOUNT_VALIDATION("Amount validated",
            "The amount was parsed, confirmed positive and normalised to the currency minor unit."),
    FRAUD_ANALYSIS("Fraud analysis",
            "The rule engine evaluated the transaction against the sender's recent behaviour."),
    RISK_SCORED("Risk scored",
            "The weighted rule scores were summed, corroborated and clamped to the 0-100 band."),
    ALERT_CREATED("Alert created",
            "The risk score reached HIGH, the funds were held and an alert was opened for review."),
    REVIEW_STARTED("Review started",
            "An administrator picked up this alert. No money has moved."),
    MARKED_SAFE("Marked safe",
            "Marked safe by administrator - funds released for settlement."),
    CONFIRMED_FRAUD("Fraud confirmed",
            "The transfer was confirmed as fraudulent. The funds stay held for manual recovery."),
    ACCOUNT_BLOCKED("Sender account blocked",
            "The sender account was blocked through the account service."),
    TRANSACTION_SETTLED("Transfer settled",
            "The transaction service reported the transfer as settled; the alert was closed."),
    TRANSACTION_FAILED("Transfer failed",
            "The transaction service reported the transfer as failed before settlement.");

    private final String label;
    private final String description;

    FraudTimelineKey(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }
}
