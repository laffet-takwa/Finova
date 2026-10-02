package com.finova.user.service;

import com.finova.common.web.CorrelationId;
import com.finova.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Read-only view of the request currently being served.
 * <p>
 * The audit trail records the address a call came from and the correlation id that
 * ties it to the rest of the platform, and both come from the HTTP layer. Wrapping
 * the lookup keeps {@link HttpServletRequest} out of every service constructor and
 * out of the unit tests: outside a servlet request the accessors return safe
 * defaults rather than throwing, so the Kafka consumer and the seeder can record
 * audit rows too.
 */
@Component
public class RequestContext {

    private static final String UNKNOWN = "unknown";

    /** Client address, preferring the first entry of a {@code X-Forwarded-For} chain. */
    public String ipAddress() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            return truncate(first);
        }
        return truncate(request.getRemoteAddr());
    }

    public String correlationId() {
        HttpServletRequest request = currentRequest();
        if (request != null) {
            return CorrelationIdFilter.current(request);
        }
        String fromMdc = MDC.get(CorrelationId.MDC_KEY);
        return fromMdc == null ? UNKNOWN : fromMdc;
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    /** {@code varchar(64)} is the widest address we store, so an over-long header is cut, not rejected. */
    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= 64 ? trimmed : trimmed.substring(0, 64);
    }
}
