package com.finova.fraud.service.rules;

import com.finova.fraud.config.FraudProperties;

/**
 * Rule 2 - BURST_VELOCITY (client spec section 13).
 * <p>
 * More than {@code max-transactions} transfers leaving the same sender account inside the window is
 * the classic automation and bot-mule signature. The score steps up once the burst is large enough
 * to rule out normal human behaviour.
 */
public class BurstVelocityRule implements FraudRule {

    public static final String ID = "BURST_VELOCITY";

    private final FraudProperties.Velocity settings;

    public BurstVelocityRule(FraudProperties.Velocity settings) {
        this.settings = settings;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "Detects bursts of transfers leaving one sender account inside a short window.";
    }

    @Override
    public RuleOutcome evaluate(FraudContext context) {
        int observed = context.windowEventCount();
        if (observed <= settings.getMaxTransactions()) {
            return RuleOutcome.notTriggered();
        }
        int score = observed > settings.getHighScoreEventCount()
                ? settings.getHighScore()
                : settings.getMediumScore();
        String reason = observed + " transactions from this account within the last "
                + settings.getWindowSeconds() + " seconds.";
        return RuleOutcome.triggered(score, reason);
    }
}
