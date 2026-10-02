package com.finova.fraud.dto;

import java.time.Instant;

/** Optional query filters of {@code GET /api/fraud/alerts}. */
public record FraudAlertFilters(
        String status,
        String riskLevel,
        String search,
        Instant from,
        Instant to,
        int page,
        int size
) {

    public static final int MAX_SIZE = 200;

    public static FraudAlertFilters of(String status, String riskLevel, String search,
                                       Instant from, Instant to, Integer page, Integer size) {
        int resolvedPage = page == null || page < 0 ? 0 : page;
        int resolvedSize = size == null || size <= 0 ? 20 : Math.min(size, MAX_SIZE);
        return new FraudAlertFilters(status, riskLevel, search, from, to, resolvedPage, resolvedSize);
    }
}
