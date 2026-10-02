package com.finova.notification.dto;

/** Full replacement of the caller's delivery preferences. */
public record PreferenceUpdateRequest(
        boolean emailEnabled,
        boolean pushEnabled,
        boolean inAppEnabled,
        boolean transferAlerts,
        boolean securityAlerts,
        boolean marketingEmails
) {
}