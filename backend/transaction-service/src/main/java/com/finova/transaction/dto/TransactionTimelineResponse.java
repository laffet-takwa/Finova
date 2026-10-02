package com.finova.transaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Derived lifecycle view of a transfer.
 * <p>
 * Every step is derived from real persisted data only; a timestamp is present
 * only where the service actually stored one. {@code NOTIFIED} stays
 * {@code PENDING} because finalising that step is the notification service's
 * job and this service genuinely does not know its outcome.
 */
@Schema(name = "TransactionTimelineResponse", description = "Derived lifecycle of a transfer")
public record TransactionTimelineResponse(
        String transactionId,
        String reference,
        List<TimelineStep> steps
) {

    public enum StepState {
        DONE,
        CURRENT,
        PENDING,
        FAILED
    }

    @Schema(name = "TimelineStep")
    public record TimelineStep(
            @Schema(allowableValues = {"CREATED", "VALIDATED", "FRAUD_CHECKED", "SETTLED",
                    "HELD_FOR_REVIEW", "NOTIFIED"}) String key,
            String label,
            String description,
            StepState state,
            Instant at
    ) {

        public static TimelineStep of(String key, String label, String description, StepState state, Instant at) {
            return new TimelineStep(key, label, description, state, at);
        }
    }
}