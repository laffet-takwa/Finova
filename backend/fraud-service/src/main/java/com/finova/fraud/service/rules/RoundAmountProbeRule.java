package com.finova.fraud.service.rules;

import com.finova.common.support.Money;
import com.finova.fraud.config.FraudProperties;

import java.math.BigDecimal;

/**
 * Rule 5 - ROUND_AMOUNT_PROBE.
 * <p>
 * Structuring: the smuggler splits a large payment into round chunks that each sit just under the
 * manual review threshold. The signal only exists when another rule corroborates it, so this rule
 * is evaluated last and stays silent unless an earlier rule fired.
 * <p>
 * Note the deliberate asymmetry with {@link LargeAmountRule}: the probe requires the amount to be
 * <em>below</em> the currency threshold. A round amount <em>above</em> the threshold is already
 * caught outright by rule 1 and needs no second helping, and scoring it twice would push the
 * canonical large-plus-unusual case past the documented 88.
 */
public class RoundAmountProbeRule implements FraudRule {

    public static final String ID = "ROUND_AMOUNT_PROBE";

    private final FraudProperties.RoundAmount settings;
    private final FraudProperties.LargeAmount largeAmount;

    public RoundAmountProbeRule(FraudProperties.RoundAmount settings,
                                FraudProperties.LargeAmount largeAmount) {
        this.settings = settings;
        this.largeAmount = largeAmount;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "Detects round amounts parked just under the review threshold as a structuring signal.";
    }

    @Override
    public RuleOutcome evaluate(FraudContext context) {
        if (!context.anotherRuleTriggered()) {
            return RuleOutcome.notTriggered();
        }
        BigDecimal amount = context.amount();
        if (amount.compareTo(settings.getMinimum()) < 0) {
            return RuleOutcome.notTriggered();
        }
        if (amount.remainder(settings.getMultiple()).compareTo(BigDecimal.ZERO) != 0) {
            return RuleOutcome.notTriggered();
        }
        BigDecimal threshold = largeAmount.thresholdFor(context.currency());
        if (amount.compareTo(threshold) >= 0) {
            return RuleOutcome.notTriggered();
        }
        return RuleOutcome.triggered(settings.getScore(),
                "Round amount of " + Money.scale(amount) + " " + context.currency()
                        + " sits below the " + Money.scale(threshold) + " " + context.currency()
                        + " review threshold, a classic structuring pattern.");
    }
}
