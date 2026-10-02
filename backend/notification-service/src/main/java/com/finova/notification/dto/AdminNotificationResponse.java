package com.finova.notification.dto;

import com.finova.common.domain.Currency;
import com.finova.common.domain.NotificationType;
import com.finova.notification.domain.NotificationCategory;
import com.finova.notification.domain.NotificationSeverity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Activity feed row for administrators.
 * <p>
 * This is deliberately a different type from {@link NotificationResponse} rather than
 * a superset of it. The customer inbox needs no {@code userId} because the caller
 * already knows who they are, and carrying it there would be surface area for
 * nothing; an operator looking at the platform-wide feed, on the other hand, cannot
 * tell whose money moved without it.
 * <p>
 * {@code userDisplayHint} is the masked owner id. It exists so an operator can
 * correlate a row with an audit entry by eye without this service calling the
 * user-service: rendering a name or an email would mean a cross-service lookup in
 * the hot path of a read, and the admin console is the right place to join against
 * {@code /api/users/admin} when a fuller identity is actually wanted.
 */
@Schema(name = "AdminNotificationResponse", description = "Activity feed row visible to administrators")
public record AdminNotificationResponse(
        String id,
        String userId,
        String userDisplayHint,
        NotificationType type,
        NotificationCategory category,
        NotificationSeverity severity,
        String title,
        String message,
        String transactionId,
        String reference,
        BigDecimal amount,
        Currency currency,
        boolean read,
        Instant readAt,
        Instant createdAt,
        String correlationId
) {
}