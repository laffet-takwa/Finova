package com.finova.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * The single error envelope returned by every Finova REST endpoint.
 * Stack traces are never serialised to clients.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String correlationId,
        Map<String, String> details
) {
    public static ApiError of(int status, ErrorCode code, String message, String path,
                              String correlationId, Map<String, String> details) {
        return new ApiError(Instant.now(), status, code.name(), message, path, correlationId, details);
    }
}
