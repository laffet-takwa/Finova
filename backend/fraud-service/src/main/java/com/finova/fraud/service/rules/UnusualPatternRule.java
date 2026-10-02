package com.finova.fraud.service.rules;

import com.finova.common.support.Money;
import com.finova.fraud.config.FraudProperties;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Rule 3 - UNUSUAL_PATTERN (client spec section 13).
 * <p>
 * Scores a transfer that does not look like the sender's recent behaviour: a material jump against
 * the rolling average, a jump against the largest previous amount, the account's very first
 * transfer, or a transfer during the bank's quiet hours. The reported reason always names the
 * observed baseline so an administrator can judge the claim instead of trusting a number.
 */
public class UnusualPatternRule implements FraudRule {

    public static final String ID = "UNUSUAL_PATTERN";

    private final FraudProperties.UnusualPattern settings;

    public UnusualPatternRule(FraudProperties.UnusualPattern settings) {
        this.settings = settings;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "Compares the transfer against the sender's recent amounts, history and activity hours.";
    }

    @Override
    public RuleOutcome evaluate(FraudContext context) {
        BigDecimal amount = context.amount();
        BigDecimal average = context.historicalAverage();

        if (context.firstEverTransfer()) {
            return RuleOutcome.triggered(settings.getScore(),
                    "First ever transfer from this account, no baseline exists yet.");
        }

        if (average.compareTo(BigDecimal.ZERO) > 0
                && amount.compareTo(average.multiply(settings.getAverageMultiplier())) > 0) {
            BigDecimal ratio = ratio(amount, average);
            return RuleOutcome.triggered(settings.getScore(),
                    "Amount is " + plain(ratio) + "x the sender's average of " + Money.scale(average)
                            + " " + context.currency() + " over the last "
                            + context.baselineSize() + " transactions.");
        }

        BigDecimal largest = context.largestPreviousAmount();
        if (largest.compareTo(BigDecimal.ZERO) > 0
                && amount.compareTo(largest.multiply(settings.getLargestMultiplier())) > 0) {
            BigDecimal ratio = ratio(amount, largest);
            return RuleOutcome.triggered(settings.getScore(),
                    "Amount is " + plain(ratio) + "x the largest previous transfer of "
                            + Money.scale(largest) + " " + context.currency() + ".");
        }

        LocalDateTime local = LocalDateTime.ofInstant(context.evaluatedAt(), ZoneId.of(settings.getZoneId()));
        int hour = local.getHour();
        if (hour >= settings.getUnusualHourStart() && hour < settings.getUnusualHourEnd()) {
            return RuleOutcome.triggered(settings.getScore(),
                    "Transfer initiated at " + String.format("%02d:00", hour) + " local time ("
                            + settings.getZoneId() + "), outside normal banking hours.");
        }
        return RuleOutcome.notTriggered();
    }

    private BigDecimal ratio(BigDecimal amount, BigDecimal baseline) {
        return amount.divide(baseline, Money.SCALE, RoundingMode.HALF_EVEN);
    }

    /** Ratios read better without trailing millimes but keep one decimal: 4.2, not 4. */
    private String plain(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() <= 0 ? stripped.setScale(1).toPlainString() : stripped.toPlainString();
    }
}
