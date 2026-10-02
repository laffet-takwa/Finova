package com.finova.transaction.mapper;

import com.finova.common.support.Money;
import com.finova.transaction.domain.Transaction;
import com.finova.transaction.dto.TransactionResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Entity to API mapping.
 * <p>
 * Hand written rather than generated: {@code AccountNumbers} exposes several
 * {@code String -> String} methods, which MapStruct cannot disambiguate for the
 * auto-mapped string properties without a wrapper bean, and the whole mapping is
 * one explicit field list plus a fee addition. {@link Money} is applied on the
 * way out so a value read from {@code numeric(19,3)} is never rendered with an
 * unexpected scale.
 */
@Component
public class TransactionMapper {

    private final AccountNumbers accountNumbers;

    public TransactionMapper(AccountNumbers accountNumbers) {
        this.accountNumbers = accountNumbers;
    }

    public TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getReference(),
                transaction.getSenderAccountId(),
                accountNumbers.mask(transaction.getSenderAccountNumber()),
                transaction.getReceiverAccountId(),
                accountNumbers.mask(transaction.getReceiverAccountNumber()),
                accountNumbers.display(transaction.getSenderAccountNumber()),
                accountNumbers.display(transaction.getReceiverAccountNumber()),
                scale(transaction.getAmount()),
                transaction.getCurrency(),
                scale(transaction.getFee()),
                total(transaction),
                transaction.getDescription(),
                transaction.getType(),
                transaction.getStatus(),
                transaction.getRiskScore(),
                transaction.getRiskLevel(),
                transaction.getRiskReasons() == null ? List.of() : transaction.getRiskReasons(),
                transaction.getFailureReason(),
                transaction.getCreatedAt(),
                transaction.getCompletedAt(),
                scale(transaction.getSettledSenderBalance()),
                scale(transaction.getSettledReceiverBalance()),
                transaction.getCorrelationId());
    }

    public List<TransactionResponse> toResponses(List<Transaction> transactions) {
        return transactions.stream().map(this::toResponse).toList();
    }

    private BigDecimal total(Transaction transaction) {
        BigDecimal amount = transaction.getAmount() == null ? Money.ZERO : transaction.getAmount();
        BigDecimal fee = transaction.getFee() == null ? Money.ZERO : transaction.getFee();
        return Money.scale(amount.add(fee));
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? null : Money.scale(value);
    }
}