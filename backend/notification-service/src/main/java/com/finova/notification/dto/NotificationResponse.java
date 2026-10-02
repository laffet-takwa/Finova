package com.finova.notification.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Inbox entry as returned to its owner.
 * <p>
 * {@code userId} is deliberately absent: the caller already knows who they are and
 * a row of their own inbox never needs to name them back.
 */
public record NotificationResponse(
        String id,
        String type,
        String category,
        String severity,
        String title,
        String message,
        String transactionId,
        String reference,
        BigDecimal amount,
        String currency,
        boolean read,
        Instant readAt,
        Instant createdAt
) {
}