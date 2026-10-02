package com.finova.common.event;

import java.time.Instant;

/** Payload consumed by the notification service to materialise a user inbox entry. */
public record NotificationRequestEvent(
        String userId,
        String type,
        String title,
        String message,
        String category,
        String transactionId,
        String reference,
        String correlationId,
        String severity
) {
    public static final String CATEGORY_TRANSACTIONS = "TRANSACTIONS";
    public static final String CATEGORY_SECURITY = "SECURITY";
    public static final String CATEGORY_SYSTEM = "SYSTEM";

    public NotificationRequestEvent {
        if (severity == null) {
            severity = "INFO";
        }
        if (category == null) {
            category = CATEGORY_SYSTEM;
        }
    }
}
