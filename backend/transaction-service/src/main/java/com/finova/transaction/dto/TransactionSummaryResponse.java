package com.finova.transaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Customer dashboard aggregate (client spec section 23).
 * <p>
 * {@code totalBalance} is deliberately absent: the balance belongs to the
 * account service, and duplicating it here would create a second source of truth.
 * A transaction where the caller is the sender counts as an expense, where the
 * caller is the receiver it counts as income.
 */
@Schema(name = "TransactionSummaryResponse", description = "Dashboard aggregates for the authenticated customer")
public record TransactionSummaryResponse(
        @Schema(example = "2500.000") BigDecimal income,
        @Schema(example = "1245.500") BigDecimal expenses,
        int transactionCount,
        @Schema(example = "-12.50") BigDecimal monthChangePercent,
        @Schema(example = "-357.500") BigDecimal monthChangeAbsolute,
        List<DailyPoint> dailySeries,
        List<TransactionResponse> recentTransactions
) {

    @Schema(name = "DailySummaryPoint")
    public record DailyPoint(
            @Schema(example = "2026-09-30") LocalDate date,
            BigDecimal income,
            BigDecimal expenses,
            int count
    ) {
    }
}