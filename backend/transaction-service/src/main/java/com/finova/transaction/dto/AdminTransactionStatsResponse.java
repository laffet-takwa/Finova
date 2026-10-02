package com.finova.transaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Schema(name = "AdminTransactionStatsResponse", description = "Platform-wide transaction statistics")
public record AdminTransactionStatsResponse(
        long totalTransactions,
        long completedToday,
        long failedToday,
        long pendingCount,
        long flaggedCount,
        @Schema(example = "97.40") BigDecimal successRate,
        @Schema(example = "184300.000") BigDecimal volumeToday,
        Map<String, BigDecimal> volumeByCurrency,
        Map<String, Long> statusDistribution,
        Map<String, Long> typeDistribution,
        List<Bucket> hourlyVolume,
        List<Bucket> dailyVolume,
        BigDecimal averageAmount,
        BigDecimal largestAmount
) {

    @Schema(name = "VolumeBucket")
    public record Bucket(
            @Schema(example = "2026-10-01T14:00:00Z") java.time.Instant from,
            @Schema(example = "2026-10-01T14:00:00Z") java.time.Instant to,
            long count,
            @Schema(example = "14200.500") BigDecimal volume
    ) {

        public static Bucket empty(java.time.Instant from, java.time.Instant to) {
            return new Bucket(from, to, 0L, BigDecimal.ZERO);
        }
    }
}