package com.finova.notification.service;

import com.finova.common.domain.NotificationType;
import com.finova.common.event.DomainEvent;
import com.finova.notification.domain.NotificationCategory;
import com.finova.notification.domain.NotificationSeverity;

import java.math.BigDecimal;

/**
 * A notification that is about to be persisted. It is a value object: it carries
 * everything the entity needs and nothing that only the persistence layer cares
 * about.
 */
public record NotificationDraft(
        String recipientUserId,
        NotificationType type,
        NotificationCategory category,
        NotificationSeverity severity,
        String title,
        String message,
        String transactionId,
        String reference,
        BigDecimal amount,
        String currency,
        String sourceEventId,
        String correlationId,
        String sourceService
) {
}