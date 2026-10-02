package com.finova.transaction.client;

/**
 * {@code GET /api/accounts/lookup?accountNumber=} response: the beneficiary-safe
 * view of an account. It deliberately carries no balance and no holder id, which
 * is why resolving an unknown number to a ledger row additionally needs
 * {@code GET /api/accounts?accountNumber=}.
 */
public record AccountLookupResponse(
        String accountNumber,
        String maskedAccountNumber,
        String accountType,
        String currency,
        String status,
        String holderDisplayName,
        String bankName
) {
}