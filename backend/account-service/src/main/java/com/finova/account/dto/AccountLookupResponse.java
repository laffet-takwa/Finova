package com.finova.account.dto;

import com.finova.common.domain.AccountStatus;
import com.finova.common.domain.AccountType;
import com.finova.common.domain.Currency;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What a customer is allowed to learn about a beneficiary before sending money:
 * never the holder name, only the masked account number and the product.
 */
@Schema(name = "AccountLookupResponse", description = "Beneficiary review details for a transfer")
public record AccountLookupResponse(
    @Schema(example = "TN58 1000 0123 4567 8901 23")
    String accountNumber,
    @Schema(example = "TN58 •••• •••• 8901 23")
    String maskedAccountNumber,
    AccountType accountType,
    Currency currency,
    @Schema(example = "TN58 •••• •••• 8901 23", description = "Deliberately not the holder name")
    String holderDisplayName,
    AccountStatus status,
    @Schema(example = "Finova Bank")
    String bankName
) {
    public static final String BANK_NAME = "Finova Bank";
}
