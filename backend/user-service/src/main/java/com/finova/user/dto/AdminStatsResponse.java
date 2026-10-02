package com.finova.user.dto;

import java.util.List;
import java.util.Map;

/**
 * Administrative user statistics.
 * <p>
 * {@code growthSeries} is one point per day over the window, including days with no
 * sign-up, so the chart's x axis is a continuous time scale rather than a sparse list
 * of days that happened to have an event.
 */
public record AdminStatsResponse(
        long totalUsers,
        long activeUsers,
        long blockedUsers,
        long newUsersThisMonth,
        List<SeriesPointResponse> growthSeries,
        Map<String, Long> usersByRole,
        Map<String, Long> auditByAction) {
}
