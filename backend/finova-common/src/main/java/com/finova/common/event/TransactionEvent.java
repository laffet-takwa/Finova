package com.finova.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Payload published on every {@code transaction.*} topic.
 * <p>
 * The same shape is reused for the create, complete, fail and flag decisions so
 * consumers can be written once. Fields that only make sense for a specific
 * decision (risk details, completion timestamps) stay null elsewhere.
 */
public record TransactionEvent(
        String transactionId,
        String reference,
        String senderAccountId,
        String receiverAccountId,
        String senderAccountNumber,
        String receiverAccountNumber,
        String senderUserId,
        String receiverUserId,
        BigDecimal amount,
        String currency,
        String description,
        String type,
        String status,
        Integer riskScore,
        String riskLevel,
        List<String> reasons,
        String failureReason,
        String requestedByUserId,
        String ipAddress,
        Instant completedAt
) {
    public List<String> safeReasons() {
        return reasons == null ? List.of() : reasons;
    }

    public boolean isFlagged() {
        return "FLAGGED".equals(status);
    }
}
