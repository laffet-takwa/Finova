package com.finova.transaction.dto;

import com.finova.common.domain.Currency;
import com.finova.common.domain.TransactionStatus;
import com.finova.common.domain.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Normalised filter for the two paginated history endpoints.
 * <p>
 * Pagination and sort are resolved and clamped by the service before this
 * reaches the repository, so no controller ever has to validate them.
 */
public record TransactionQuery(
        String search,
        TransactionType type,
        TransactionStatus status,
        String accountId,
        Currency currency,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        Instant from,
        Instant to,
        int page,
        int size,
        String sort
) {
}