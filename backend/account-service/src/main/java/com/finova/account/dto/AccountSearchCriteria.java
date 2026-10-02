package com.finova.account.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Optional admin search filters. A blank or absent filter is stored as
 * {@code null} so the repository applies no restriction for it.
 */
public record AccountSearchCriteria(
    String search,
    String status,
    String accountType,
    String currency,
    String userId,
    BigDecimal minBalance,
    BigDecimal maxBalance,
    Instant createdFrom,
    Instant createdTo
) {

    public static AccountSearchCriteria of(String search, String status, String accountType, String currency,
                                           String userId, BigDecimal minBalance, BigDecimal maxBalance,
                                           Instant from, Instant to) {
        return new AccountSearchCriteria(trimToNull(search), trimToNull(status), trimToNull(accountType),
            trimToNull(currency), trimToNull(userId), minBalance, maxBalance, from, to);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
