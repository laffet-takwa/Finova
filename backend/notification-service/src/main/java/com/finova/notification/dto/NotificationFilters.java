package com.finova.notification.dto;

import java.time.Instant;

/**
 * Customer facing inbox filters.
 * <p>
 * There is no {@code userId} field and there never will be one: the owner is taken
 * from the access token by the service. A caller cannot widen the query beyond
 * their own rows, whatever they append to the URL.
 *
 * @param type       {@code NotificationType} name, optional
 * @param category   {@code NotificationCategory} value, optional
 * @param unreadOnly when true, only entries the user has not opened yet
 * @param search     case-insensitive substring of the title or message, optional
 * @param from       inclusive lower bound on creation time, optional
 * @param to         exclusive upper bound on creation time, optional
 * @param page       zero based page index
 * @param size       page size, clamped by the service
 */
public record NotificationFilters(
        String type,
        String category,
        boolean unreadOnly,
        String search,
        Instant from,
        Instant to,
        int page,
        int size
) {
    public NotificationFilters {
        page = Math.max(page, 0);
        size = size <= 0 ? 20 : size;
    }
}