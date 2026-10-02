package com.finova.common.event;

import java.math.BigDecimal;

/**
 * Payload published on {@code account.opened}.
 * <p>
 * The account-service owns the product (holder, type, status) while the
 * transaction-service owns the authoritative balance. This event carries the
 * opening balance so the transaction service can create its ledger projection
 * without a synchronous call, and it carries later edits so the projection
 * stays aligned.
 */
public record AccountOpenedEvent(
        String accountId,
        String userId,
        String accountNumber,
        String accountType,
        String currency,
        String status,
        BigDecimal openingBalance,
        boolean restatement
) {
}
