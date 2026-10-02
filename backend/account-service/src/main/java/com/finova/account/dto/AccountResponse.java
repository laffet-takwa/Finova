package com.finova.account.dto;

import com.finova.common.domain.AccountStatus;
import com.finova.common.domain.AccountType;
import com.finova.common.domain.Currency;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "AccountResponse", description = "A bank account held by a customer")
public record AccountResponse(
    String id,
    @Schema(example = "TN58 1000 0123 4567 8901 23", description = "Formatted for display")
    String accountNumber,
    @Schema(example = "TN58 •••• •••• 8901 23")
    String maskedAccountNumber,
    String userId,
    AccountType accountType,
    Currency currency,
    @Schema(example = "12450.750", description = "Read projection maintained from transaction.completed")
    BigDecimal balance,
    BigDecimal availableBalance,
    AccountStatus status,
    String nickname,
    @Schema(example = "TN565900058100001234567890123")
    String iban,
    Instant createdAt,
    Instant updatedAt,
    @Schema(example = "Finova Bank", description = "Institution holding the account")
    String bankName
) {
}
