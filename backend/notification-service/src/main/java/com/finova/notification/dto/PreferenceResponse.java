package com.finova.notification.dto;

import java.time.Instant;

/** Delivery preferences of the calling user. */
public record PreferenceResponse(
        boolean emailEnabled,
        boolean pushEnabled,
        boolean inAppEnabled,
        boolean transferAlerts,
        boolean securityAlerts,
        boolean marketingEmails,
        Instant updatedAt
) {
}