package com.finova.common.event;

import java.time.Instant;

/** Payload published on {@code notification.created} once an inbox entry is persisted. */
public record NotificationCreatedEvent(
        String notificationId,
        String userId,
        String type,
        String category,
        String title,
        String severity,
        Instant createdAt
) {
}
