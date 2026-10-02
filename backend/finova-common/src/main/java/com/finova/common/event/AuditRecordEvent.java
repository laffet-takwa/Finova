package com.finova.common.event;

import java.time.Instant;
import java.util.Map;

/**
 * Payload published on {@code audit.recorded}.
 * <p>
 * The user-service is the system of record for the audit trail: every service
 * publishes here, and the audit store is written from a single consumer, which
 * guarantees an ordered, append-only history of sensitive operations.
 */
public record AuditRecordEvent(
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
        Instant occurredAt
) {
    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_FAILURE = "FAILURE";
}
