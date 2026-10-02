package com.finova.fraud.service.rules;

import com.finova.common.event.TransactionEvent;
import com.finova.fraud.domain.VelocityWindow;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** Builds deterministic rule inputs so every engine test states its own scenario in one line. */
public final class FraudContexts {

    public static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");
    public static final String SENDER_ACCOUNT = "acc-sender-1";
    public static final String RECEIVER_ACCOUNT = "acc-receiver-1";

    private FraudContexts() {
    }

    public static TransactionEvent transaction(BigDecimal amount, String currency) {
        return transaction(amount, currency, NOW);
    }

    public static TransactionEvent transaction(BigDecimal amount, String currency, Instant at) {
        return new TransactionEvent(
                "txn-1",
                "TX-20260615-00042",
                SENDER_ACCOUNT,
                RECEIVER_ACCOUNT,
                "TN12345678",
                "TN87654321",
                "usr-sender-1",
                "usr-receiver-1",
                amount,
                currency,
                "Rent",
                "TRANSFER",
                "PENDING",
                null,
                null,
                List.of(),
                null,
                "usr-sender-1",
                "10.0.0.1",
                null);
    }

    /** A window that looks like a settled account with a stable transfer history. */
    public static VelocityWindow steadyWindow(BigDecimal... previousAmounts) {
        List<BigDecimal> amounts = new ArrayList<>();
        for (BigDecimal amount : previousAmounts) {
            amounts.add(amount);
        }
        return VelocityWindow.builder()
                .id(VelocityWindow.idFor(SENDER_ACCOUNT, "TND"))
                .senderAccountId(SENDER_ACCOUNT)
                .currency("TND")
                .windowStart(NOW.minusSeconds(30))
                .eventCount(1)
                .totalAmount(amounts.isEmpty() ? BigDecimal.ZERO : amounts.get(0))
                .recentAmounts(amounts)
                .transfersSeen(Math.max(1, amounts.size()))
                .firstSeenAt(NOW.minus(90, ChronoUnit.DAYS))
                .updatedAt(NOW)
                .build();
    }

    /** A window that already counts several transfers inside the current velocity window. */
    public static VelocityWindow burstWindow(int eventCount, BigDecimal... previousAmounts) {
        VelocityWindow window = steadyWindow(previousAmounts);
        window.setEventCount(eventCount);
        window.setTransfersSeen(Math.max(window.getTransfersSeen(), eventCount));
        return window;
    }

    public static FraudContext context(TransactionEvent transaction, VelocityWindow window,
                                       boolean recipientSeen, Instant at) {
        return FraudContext.of(transaction, window, recipientSeen, at);
    }

    public static FraudContext context(TransactionEvent transaction, VelocityWindow window) {
        return FraudContext.of(transaction, window, true, NOW);
    }
}