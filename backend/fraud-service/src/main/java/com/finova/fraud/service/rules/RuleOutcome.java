package com.finova.fraud.service.rules;

/**
 * Result of a single rule evaluation.
 *
 * @param score     points this rule contributes to the transaction risk score
 * @param reason    human readable justification surfaced in the alert
 * @param triggered whether the rule's condition held
 */
public record RuleOutcome(int score, String reason, boolean triggered) {

    private static final RuleOutcome NOT_TRIGGERED = new RuleOutcome(0, null, false);

    public static RuleOutcome triggered(int score, String reason) {
        return new RuleOutcome(score, reason, true);
    }

    public static RuleOutcome notTriggered() {
        return NOT_TRIGGERED;
    }

    public static RuleOutcome notTriggered(String reason) {
        return new RuleOutcome(0, reason, false);
    }
}
