package com.finova.transaction.service;

import com.finova.common.domain.TransactionType;
import com.finova.common.support.Money;

import java.math.BigDecimal;

/**
 * The normalised, validated outcome of a transfer request: the amount and
 * currency re-scaled to {@link Money#SCALE} and the fee this service charges.
 */
public record TransferValidation(BigDecimal amount, String currency, BigDecimal fee) {

    public TransactionType type() {
        return TransactionType.TRANSFER;
    }
}