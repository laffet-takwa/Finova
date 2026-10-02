package com.finova.account.dto;

import com.finova.common.domain.AccountType;
import com.finova.common.domain.Currency;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(name = "CreateAccountRequest", description = "Payload to open a new bank account")
public record CreateAccountRequest(

    @NotNull(message = "accountType is required")
    @Schema(example = "CHECKING", allowableValues = {"CHECKING", "SAVINGS"})
    AccountType accountType,

    @NotNull(message = "currency is required")
    @Schema(example = "TND", allowableValues = {"TND", "EUR", "USD"})
    Currency currency,

    @Size(max = 40, message = "nickname must not exceed 40 characters")
    @Schema(example = "Everyday Account", maxLength = 40)
    String nickname,

    @DecimalMin(value = "0.000", message = "openingBalance must not be negative")
    @Digits(integer = 16, fraction = 3, message = "openingBalance supports at most 3 decimals")
    @Schema(example = "2500.000", description = "Defaults to 2500.000 TND for CHECKING and 5000.000 for SAVINGS, capped at 50000.000")
    BigDecimal openingBalance,

    @Schema(description = "Holder user id. ADMIN only: ignored for a CUSTOMER, who always owns the new account.")
    String userId
) {
}
