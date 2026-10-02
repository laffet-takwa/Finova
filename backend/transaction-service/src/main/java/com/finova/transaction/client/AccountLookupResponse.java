package com.finova.transaction.client;

/**
 * {@code GET /api/accounts/lookup?accountNumber=} response: the beneficiary view
 * of an account.
 * <p>
 * It carries no balance - this service owns the money - but it does carry
 * {@code accountId} and {@code userId}, which is what makes it sufficient on its
 * own to turn a customer-typed account number into a ledger row and to address
 * the receiver on {@code TransactionEvent}. {@code holderDisplayName} stays
 * masked, so a lookup can never leak the holder's identity.
 */
public record AccountLookupResponse(
        String accountId,
        String userId,
        String accountNumber,
        String maskedAccountNumber,
        String accountType,
        String currency,
        String status,
        String holderDisplayName,
        String bankName
) {
}