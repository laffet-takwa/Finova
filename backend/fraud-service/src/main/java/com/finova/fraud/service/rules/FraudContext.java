package com.finova.fraud.service.rules;

import com.finova.common.event.TransactionEvent;
import com.finova.common.support.Money;
import com.finova.fraud.domain.VelocityWindow;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable input for a rule evaluation.
 *
 * @param transaction            the {@code transaction.created} payload
 * @param window                 sender velocity snapshot taken <em>after</em> this transfer was counted
 * @param historicalAverage      mean of {@code window.recentAmounts}, zero when nothing was observed
 * @param recipientSeenRecently  whether the sender already paid this receiver inside the lookback
 * @param anotherRuleTriggered   whether an earlier rule in the evaluation order fired; lets a rule
 *                               such as the structuring probe stay silent unless corroborated
 * @param evaluatedAt            the instant the analysis ran
 */
public record FraudContext(
        TransactionEvent transaction,
        VelocityWindow window,
        BigDecimal historicalAverage,
        boolean recipientSeenRecently,
        boolean anotherRuleTriggered,
        Instant evaluatedAt
) {

    public static FraudContext of(TransactionEvent transaction, VelocityWindow window,
                                  boolean recipientSeenRecently, Instant evaluatedAt) {
        return new FraudContext(transaction, window, window == null
                ? BigDecimal.ZERO : window.averageAmount(), recipientSeenRecently, false, evaluatedAt);
    }

    /** Copy carrying the "has any earlier rule fired" flag used by corroboration-sensitive rules. */
    public FraudContext withAnotherRuleTriggered(boolean triggered) {
        return new FraudContext(transaction, window, historicalAverage, recipientSeenRecently,
                triggered, evaluatedAt);
    }

    public BigDecimal amount() {
        BigDecimal value = transaction.amount();
        return value == null ? BigDecimal.ZERO : Money.scale(value);
    }

    public String currency() {
        return transaction.currency();
    }

    public String senderAccountId() {
        return transaction.senderAccountId();
    }

    public String receiverAccountId() {
        return transaction.receiverAccountId();
    }

    /** Events counted in the live velocity window, including the transfer under analysis. */
    public int windowEventCount() {
        return window == null ? 0 : window.getEventCount();
    }

    public BigDecimal largestPreviousAmount() {
        return window == null ? BigDecimal.ZERO : window.largestAmount();
    }

    /** Number of transfers in the retained baseline, quoted in the rule reasons. */
    public int baselineSize() {
        return window == null ? 0 : window.amountsOrEmpty().size();
    }

    public boolean firstEverTransfer() {
        return window == null || window.isFirstEverTransfer();
    }

    public boolean amountPositive() {
        return amount().compareTo(BigDecimal.ZERO) > 0;
    }
}
