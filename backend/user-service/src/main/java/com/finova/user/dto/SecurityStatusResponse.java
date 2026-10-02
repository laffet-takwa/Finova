package com.finova.user.dto;

import java.time.Instant;
import java.util.List;

/**
 * The customer's security panel.
 * <p>
 * {@code twoFactorEnabled} and {@code mfaConfigured} are hard-coded false because
 * second factors are out of scope for this service: there is no enrolment endpoint,
 * no secret storage and no challenge flow, so claiming either would be a lie the UI
 * would render as "enabled". {@code lastPasswordChange} is derived from the audit
 * trail, so it is null for an identity whose password has never been changed.
 */
public record SecurityStatusResponse(
        boolean passwordProtected,
        boolean twoFactorEnabled,
        boolean mfaConfigured,
        Instant lastPasswordChange,
        String locationSource,
        List<SecurityLoginEntry> recentLogins) {

    /** The only location value this backend can honestly produce. */
    public static final String LOCATION_SOURCE = "NOT_PROVIDED_BY_BACKEND";
}
