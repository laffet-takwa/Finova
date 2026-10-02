package com.finova.notification.dto;

import java.util.Map;

/**
 * Unread badge payload. {@code byCategory} always carries all three folders, so
 * the SPA can render a per-tab counter without probing for missing keys.
 */
public record UnreadCountResponse(
        long unread,
        Map<String, Long> byCategory
) {
}