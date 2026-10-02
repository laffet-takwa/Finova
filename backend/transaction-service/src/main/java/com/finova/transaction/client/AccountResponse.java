package com.finova.transaction.client;

import java.math.BigDecimal;

/**
 * {@code GET /api/accounts/{id}} response as consumed by the transaction service.
 * <p>
 * {@code balance} is only ever read when the ledger projection is created for the
 * first time; from then on {@code ledger_account.balance} is authoritative and
 * this field is informational.
 */
public record AccountResponse(
        String id,
        String accountNumber,
        String userId,
        String accountType,
        String currency,
        BigDecimal balance,
        String status,
        String nickname,
        String bankName,
        java.time.Instant createdAt
) {
}