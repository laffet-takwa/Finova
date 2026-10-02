package com.finova.account.dto;

import com.finova.common.domain.AccountStatus;
import com.finova.common.domain.Currency;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "BalanceResponse", description = "Projected balance of a single account")
public record BalanceResponse(
    String accountId,
    @Schema(example = "TN58 1000 0123 4567 8901 23")
    String accountNumber,
    Currency currency,
    BigDecimal balance,
    BigDecimal availableBalance,
    AccountStatus status,
    @Schema(description = "Instant the projection was read")
    Instant asOf
) {
}
