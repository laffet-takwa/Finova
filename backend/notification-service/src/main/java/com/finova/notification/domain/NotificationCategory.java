package com.finova.notification.domain;

import com.finova.common.event.NotificationRequestEvent;

/**
 * Inbox folder an entry belongs to. The wire values are the canonical constants
 * of {@link NotificationRequestEvent} so a category can round-trip through a
 * Kafka payload and the database without a translation table.
 */
public enum NotificationCategory {
    TRANSACTIONS,
    SECURITY,
    SYSTEM;

    public String value() {
        return switch (this) {
            case TRANSACTIONS -> NotificationRequestEvent.CATEGORY_TRANSACTIONS;
            case SECURITY -> NotificationRequestEvent.CATEGORY_SECURITY;
            case SYSTEM -> NotificationRequestEvent.CATEGORY_SYSTEM;
        };
    }

    public static NotificationCategory from(String value) {
        for (NotificationCategory category : values()) {
            if (category.value().equalsIgnoreCase(value) || category.name().equalsIgnoreCase(value)) {
                return category;
            }
        }
        throw new IllegalArgumentException("Unknown notification category: " + value);
    }
}