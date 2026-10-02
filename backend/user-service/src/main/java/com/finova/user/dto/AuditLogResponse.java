package com.finova.user.dto;

import java.time.Instant;
import java.util.Map;

/**
 * One row of the platform audit trail.
 * <p>
 * {@code metadata} is whatever key set the producing service chose to attach. It is
 * strings only and never contains a credential, a password hash or a balance: an
 * audit payload is a record of what happened, not a copy of the data it happened to.
 */
public record AuditLogResponse(
        String id,
        String action,
        String userId,
        String resource,
        String resourceId,
        String ipAddress,
        String correlationId,
        String result,
        String service,
        String message,
        Map<String, String> metadata,
        Instant createdAt) {
}
