package com.finova.transaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * {@code POST /api/transactions} body (client spec section 10).
 * <p>
 * Bean validation only covers structural problems here. Every business rule -
 * ownership, account status, amount bounds, balance, currency - is enforced by
 * {@code TransferRequestValidator} so each one can map to its own
 * {@code ErrorCode}.
 */
@Schema(name = "TransferRequest", description = "Money transfer instruction")
public record TransferRequest(

        @Schema(description = "Ledger sender account id", example = "9c1f4a2e-0d5b-4f7c-9a11-2b3c4d5e6f70")
        @NotBlank(message = "senderAccountId is required")
        @Size(max = 36)
        String senderAccountId,

        @Schema(description = "Beneficiary account number", example = "TN581000012345678901")
        @NotBlank(message = "receiverAccountNumber is required")
        @Size(max = 34)
        String receiverAccountNumber,

        @Schema(description = "Amount to move", example = "250.00")
        @NotNull(message = "amount is required")
        @Digits(integer = 16, fraction = 3, message = "amount supports at most 3 decimals")
        BigDecimal amount,

        @Schema(description = "Settlement currency", example = "TND", allowableValues = {"TND", "EUR", "USD"})
        @NotBlank(message = "currency is required")
        @Size(min = 3, max = 3)
        String currency,

        @Schema(description = "Free text shown in the customer history", example = "Monthly payment")
        @Size(max = 255)
        String description
) {
}