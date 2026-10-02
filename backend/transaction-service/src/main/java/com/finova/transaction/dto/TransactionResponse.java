package com.finova.transaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Customer-facing view of one transfer.
 * <p>
 * Account numbers are always masked: this service owns the ledger, the account
 * service owns the product, and the raw number never leaves either service in a
 * customer response.
 */
@Schema(name = "TransactionResponse", description = "A money transfer")
public record TransactionResponse(
        @Schema(example = "b1f2c3d4-0001-4a2b-9c3d-4e5f6a7b8c9d") String id,
        @Schema(example = "TX-20261001-00001") String reference,
        String senderAccountId,
        @Schema(example = "•••• 8901") String senderAccountNumber,
        String receiverAccountId,
        @Schema(example = "•••• 6745") String receiverAccountNumber,
        @Schema(example = "Account •••• 01") String senderDisplay,
        @Schema(example = "Account •••• 45") String receiverDisplay,
        @Schema(example = "250.000") BigDecimal amount,
        @Schema(example = "TND") String currency,
        @Schema(example = "0.000") BigDecimal fee,
        @Schema(example = "250.000") BigDecimal totalAmount,
        @Schema(example = "Monthly payment") String description,
        @Schema(allowableValues = {"TRANSFER", "DEPOSIT", "WITHDRAWAL"}) String type,
        @Schema(allowableValues = {"PENDING", "PROCESSING", "COMPLETED", "FAILED", "REJECTED", "FLAGGED"}) String status,
        @Schema(example = "12") Integer riskScore,
        @Schema(allowableValues = {"LOW", "MEDIUM", "HIGH"}) String riskLevel,
        List<String> riskReasons,
        String failureReason,
        Instant createdAt,
        Instant completedAt,
        BigDecimal settledSenderBalance,
        BigDecimal settledReceiverBalance,
        String correlationId
) {
}