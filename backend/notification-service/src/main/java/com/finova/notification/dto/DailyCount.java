package com.finova.notification.dto;

/** One day of the seven day window exposed by {@link NotificationStatsResponse}. */
public record DailyCount(String date, long count) {
}