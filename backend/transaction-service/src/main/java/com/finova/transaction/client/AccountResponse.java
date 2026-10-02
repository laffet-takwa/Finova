package com.finova.transaction.client;

import java.math.BigDecimal;

/**
 * {@code GET /api/accounts/{id}} response as consumed by the transaction service.
 * <p>
 * Used for the sender leg only. {@code balance} is read exactly once - when the
 * sender's ledger projection is created - and becomes the opening balance; from
 * then on {@code ledger_account.balance} is authoritative and this field is
 * informational. The receiver leg uses {@link AccountLookupResponse}, which
 * deliberately carries no balance.
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