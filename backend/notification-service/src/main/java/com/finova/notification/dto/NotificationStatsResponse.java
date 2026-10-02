package com.finova.notification.dto;

import java.util.List;
import java.util.Map;

/**
 * Inbox statistics for the notification centre.
 * <p>
 * {@code last7Days} is the number of entries created on each of the last seven
 * days (UTC), zero-filled so the chart always has seven points.
 * {@code unreadTrend} is the subset of those days that is still unread today.
 */
public record NotificationStatsResponse(
        long total,
        long unread,
        Map<String, Long> byType,
        Map<String, Long> byCategory,
        List<DailyCount> last7Days,
        List<DailyCount> unreadTrend
) {
}