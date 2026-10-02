package com.finova.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Propagates (or creates) the request correlation id, exposing it to the
 * request scope, the logging MDC and the HTTP response headers.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String MDC_REQUEST_PATH = "requestPath";
    public static final String MDC_METHOD = "httpMethod";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String incoming = request.getHeader(CorrelationId.HEADER);
        String correlationId = (incoming == null || incoming.isBlank())
                ? UUID.randomUUID().toString()
                : incoming.trim();

        MDC.put(CorrelationId.MDC_KEY, correlationId);
        MDC.put(MDC_REQUEST_PATH, request.getRequestURI());
        MDC.put(MDC_METHOD, request.getMethod());
        request.setAttribute(CorrelationId.REQUEST_ATTRIBUTE, correlationId);
        response.setHeader(CorrelationId.HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CorrelationId.MDC_KEY);
            MDC.remove(MDC_REQUEST_PATH);
            MDC.remove(MDC_METHOD);
        }
    }

    /** Reads the correlation id for the request currently being processed. */
    public static String current(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationId.REQUEST_ATTRIBUTE);
        return value == null ? "unknown" : value.toString();
    }
}
