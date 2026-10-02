package com.finova.common.web;

/**
 * Correlation id contract shared by every Finova service.
 * The api-gateway injects the id, services echo it back in responses and
 * structured logs, and Kafka event payloads carry it end to end.
 */
public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";
    public static final String REQUEST_ATTRIBUTE = CorrelationId.class.getName() + ".value";

    private CorrelationId() {
    }
}
