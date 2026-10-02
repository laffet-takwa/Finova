package com.finova.fraud.service.rules;

import com.finova.common.support.Money;
import com.finova.fraud.config.FraudProperties;

import java.math.BigDecimal;

/**
 * Rule 1 - LARGE_AMOUNT (client spec section 13).
 * <p>
 * Amounts strictly above the currency's review threshold are high risk on their own. The threshold
 * is currency aware because a "large" transfer means something very different in TND and in EUR.
 */
public class LargeAmountRule implements FraudRule {

    public static final String ID = "LARGE_AMOUNT";

    private final FraudProperties.LargeAmount settings;

    public LargeAmountRule(FraudProperties.LargeAmount settings) {
        this.settings = settings;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "Flags transfers above the manual review threshold for the settlement currency.";
    }

    @Override
    public RuleOutcome evaluate(FraudContext context) {
        BigDecimal amount = context.amount();
        BigDecimal threshold = settings.thresholdFor(context.currency());
        if (amount.compareTo(threshold) <= 0) {
            return RuleOutcome.notTriggered();
        }
        String reason = "Large transaction amount of " + Money.scale(amount) + " " + context.currency()
                + " exceeds the " + Money.scale(threshold) + " " + context.currency()
                + " review threshold.";
        return RuleOutcome.triggered(settings.getScore(), reason);
    }
}
