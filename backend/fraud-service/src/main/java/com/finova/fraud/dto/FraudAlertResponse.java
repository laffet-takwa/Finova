package com.finova.fraud.dto;

import com.finova.common.domain.Currency;
import com.finova.common.domain.FraudStatus;
import com.finova.common.domain.RiskLevel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Public representation of a fraud assessment. The Mongo document never leaves the service layer. */
public record FraudAlertResponse(
        String id,
        String transactionId,
        String reference,
        String senderAccountId,
        String receiverAccountId,
        String senderAccountNumber,
        String senderUserId,
        BigDecimal amount,
        String currency,
        int riskScore,
        String riskLevel,
        List<String> reasons,
        List<String> triggeredRules,
        String status,
        Instant createdAt,
        Instant updatedAt,
        Instant reviewedAt,
        String reviewedBy,
        String reviewNote,
        List<TimelineStepResponse> timeline
) {
    public Currency currencyEnum() {
        return currency == null ? null : Currency.valueOf(currency);
    }

    public FraudStatus statusEnum() {
        return status == null ? null : FraudStatus.valueOf(status);
    }

    public RiskLevel riskLevelEnum() {
        return riskLevel == null ? null : RiskLevel.valueOf(riskLevel);
    }
}
