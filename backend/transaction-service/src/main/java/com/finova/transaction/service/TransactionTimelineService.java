package com.finova.transaction.service;

import com.finova.common.domain.TransactionStatus;
import com.finova.transaction.domain.Transaction;
import com.finova.transaction.dto.TransactionTimelineResponse;
import com.finova.transaction.dto.TransactionTimelineResponse.StepState;
import com.finova.transaction.dto.TransactionTimelineResponse.TimelineStep;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Derives the lifecycle a transfer went through.
 * <p>
 * Nothing here is invented. Each step is DONE, CURRENT or FAILED only when a
 * column on the row proves it, and {@code at} is populated only where this
 * service actually stored a timestamp. {@code NOTIFIED} therefore stays PENDING:
 * finalising that step belongs to the notification service and its outcome is not
 * observable from here.
 */
@Service
public class TransactionTimelineService {

    private static final String HELD = "HELD_FOR_REVIEW";
    private static final String SETTLED = "SETTLED";

    public TransactionTimelineResponse timeline(Transaction transaction) {
        List<TimelineStep> steps = new ArrayList<>(5);
        steps.add(created(transaction));
        steps.add(validated(transaction));
        steps.add(fraudChecked(transaction));
        steps.add(settled(transaction));
        steps.add(notified());
        return new TransactionTimelineResponse(transaction.getId(), transaction.getReference(), steps);
    }

    private TimelineStep created(Transaction transaction) {
        String description = "PENDING".equals(transaction.getStatus())
                ? "Transfer accepted and queued for fraud analysis."
                : "Transfer accepted and sent for fraud analysis.";
        return TimelineStep.of("CREATED", "Transfer created", description, StepState.DONE,
                transaction.getCreatedAt());
    }

    private TimelineStep validated(Transaction transaction) {
        boolean decided = isDecided(transaction.getStatus());
        if (!decided) {
            return TimelineStep.of("VALIDATED", "Account validation",
                    "Awaiting validation of the accounts, amount and currency.",
                    StepState.CURRENT, null);
        }
        StepState state = isFailure(transaction.getStatus()) ? StepState.FAILED : StepState.DONE;
        return TimelineStep.of("VALIDATED", "Account validation",
                "Accounts, amount and currency validated against the ledger.",
                state, transaction.getCompletedAt());
    }

    private TimelineStep fraudChecked(Transaction transaction) {
        if (transaction.getRiskScore() == null) {
            return TimelineStep.of("FRAUD_CHECKED", "Fraud analysis",
                    "Awaiting the fraud engine decision.", StepState.CURRENT, null);
        }
        String level = transaction.getRiskLevel() == null
                ? "UNKNOWN" : transaction.getRiskLevel().toUpperCase(Locale.ROOT);
        return TimelineStep.of("FRAUD_CHECKED", "Fraud analysis",
                "Risk engine returned a score of " + transaction.getRiskScore() + " (" + level + ").",
                StepState.DONE, null);
    }

    private TimelineStep settled(Transaction transaction) {
        if (TransactionStatus.FLAGGED.name().equals(transaction.getStatus())) {
            return TimelineStep.of(HELD, "Held for review",
                    "Funds are held pending a manual fraud review. No money has moved.",
                    StepState.CURRENT, null);
        }
        if (TransactionStatus.COMPLETED.name().equals(transaction.getStatus())) {
            return TimelineStep.of(SETTLED, "Settled",
                    "Balances debited and credited and both ledger legs written.",
                    StepState.DONE, transaction.getCompletedAt());
        }
        if (isFailure(transaction.getStatus())) {
            return TimelineStep.of(SETTLED, "Settlement",
                    nullSafe(transaction.getFailureReason(), "Settlement did not complete. No money moved."),
                    StepState.FAILED, transaction.getCompletedAt());
        }
        return TimelineStep.of(SETTLED, "Settlement",
                "Awaiting settlement once the fraud engine clears the transfer.",
                StepState.PENDING, null);
    }

    private TimelineStep notified() {
        return TimelineStep.of("NOTIFIED", "Notification",
                "The notification service finalises this step once it consumes the terminal event.",
                StepState.PENDING, null);
    }

    private boolean isDecided(String status) {
        return isFailure(status) || TransactionStatus.COMPLETED.name().equals(status)
                || TransactionStatus.FLAGGED.name().equals(status);
    }

    private boolean isFailure(String status) {
        return TransactionStatus.FAILED.name().equals(status)
                || TransactionStatus.REJECTED.name().equals(status);
    }

    private String nullSafe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}