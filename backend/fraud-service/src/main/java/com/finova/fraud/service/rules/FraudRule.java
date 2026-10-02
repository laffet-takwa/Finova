package com.finova.fraud.service.rules;

/**
 * A pluggable fraud rule.
 * <p>
 * Implementations must stay free of framework types and side effects: they receive an immutable
 * snapshot and return a value. Adding a rule means implementing this interface and listing it in
 * {@code FraudRuleConfig}; nothing else in the service changes.
 */
public interface FraudRule {

    /** Stable identifier persisted on the alert, e.g. {@code LARGE_AMOUNT}. */
    String id();

    /** Human readable description shown to the reviewing administrator. */
    String description();

    RuleOutcome evaluate(FraudContext context);
}
