package com.finova.common.event;

import java.math.BigDecimal;
import java.time.Instant;

/** Payload published on {@code account.blocked}. */
public record AccountBlockedEvent(
        String accountId,
        String userId,
        String accountNumber,
        String previousStatus,
        String status,
        String reason,
        String blockedBy,
        BigDecimal balance,
        String currency,
        Instant blockedAt
) {
}
