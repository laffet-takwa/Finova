package com.finova.account.dto;

import com.finova.common.domain.AccountStatus;
import com.finova.common.domain.AccountType;
import com.finova.common.domain.Currency;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What a payer is allowed to learn about the beneficiary before sending money.
 * <p>
 * {@code holderDisplayName} is the masked account number and never a customer name:
 * the account table holds no name at all, and the payer must not learn who they are
 * about to pay. Nothing money related is disclosed either, no balance, no
 * transaction history, no contact details.
 * <p>
 * {@code accountId} and {@code userId} ARE disclosed, on purpose:
 * <ul>
 *   <li>the caller must already know the exact account number; typing it is the premise
 *       of the feature, and a wrong digit simply resolves to nothing,</li>
 *   <li>both are opaque UUIDs that disclose nothing by themselves: there is no
 *       customer-accessible lookup by user id ({@code GET /api/users/admin/{id}} is
 *       ADMIN only) and {@code GET /api/accounts/{accountId}} is ownership enforced, so
 *       knowing a {@code userId} does not reveal a name or any profile detail,</li>
 *   <li>without them the payer cannot address the beneficiary, and the platform is
 *       then unable to accept a payment to a recipient at all.</li>
 * </ul>
 * Do not "tighten" this response by dropping those two identifiers: it breaks customer
 * to customer transfers.
 */
@Schema(name = "AccountLookupResponse", description = "Beneficiary review details for a transfer")
public record AccountLookupResponse(
    @Schema(example = "7b1f0c2e-4d5a-4f8b-9c3e-1a2b3c4d5e6f",
        description = "Opaque account identifier the payer needs to address the beneficiary")
    String accountId,
    @Schema(example = "3f6d9a1c-8e2b-4c7d-8f1a-2b3c4d5e6f70",
        description = "Opaque owner identifier; resolves to nothing without ADMIN rights")
    String userId,
    @Schema(example = "TN58 1000 0123 4567 8901 23")
    String accountNumber,
    @Schema(example = "TN58 •••• •••• 8901 23")
    String maskedAccountNumber,
    AccountType accountType,
    Currency currency,
    AccountStatus status,
    @Schema(example = "TN58 •••• •••• 8901 23", description = "Deliberately not the holder name")
    String holderDisplayName,
    @Schema(example = "Finova Bank")
    String bankName
) {
    public static final String BANK_NAME = "Finova Bank";
}