package com.finova.fraud.dto;

import java.util.List;
import java.util.Map;

/** Dashboard summary for the fraud review console (client spec section 35). */
public record FraudStatsResponse(
        long openAlerts,
        long highRisk,
        long mediumRisk,
        long lowRisk,
        long resolvedToday,
        long confirmedToday,
        long totalAssessedToday,
        double averageRiskScore,
        Map<String, Long> riskDistribution,
        Map<String, Long> statusDistribution,
        List<SeriesPointResponse> dailyAlerts,
        List<TopRiskyAccountResponse> topRiskyAccounts
) {

    /** Zero-filled point so the chart never shows a gap for a day without alerts. */
    public record SeriesPointResponse(String label, long count) {
    }

    public record TopRiskyAccountResponse(String accountId, String accountNumber,
                                          long count, int maxRiskScore) {
    }
}
