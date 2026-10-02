package com.finova.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Schema(name = "AccountStatsResponse", description = "Aggregated account statistics for the admin dashboard")
public record AccountStatsResponse(
    long totalAccounts,
    long activeAccounts,
    long blockedAccounts,
    long closedAccounts,
    @Schema(example = "{\"TND\": 41204.000, \"EUR\": 6400.000}")
    Map<String, BigDecimal> totalBalanceByCurrency,
    @Schema(example = "{\"CHECKING\": 5, \"SAVINGS\": 3}")
    Map<String, Long> accountsByType,
    long newAccountsLast30Days,
    @Schema(description = "Accounts opened per day over the last 30 days, zero filled")
    List<SeriesPoint> growthSeries,
    @Schema(description = "Balance of accounts opened per day over the last 30 days, zero filled")
    List<SeriesPoint> dailyBalanceSeries
) {
}
