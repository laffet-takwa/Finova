package com.finova.transaction.service;

import com.finova.common.error.BusinessException;
import com.finova.common.error.ErrorCode;
import com.finova.common.support.Money;
import com.finova.transaction.config.TransactionProperties;
import com.finova.transaction.domain.LedgerAccount;
import com.finova.transaction.dto.TransferRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;

/**
 * The business rules of a transfer, in the exact order the client specification
 * mandates (sections 3 to 9), each with its own canonical {@link ErrorCode}.
 * <p>
 * Rules 2 and 4 - resolving the two accounts - need the account directory and
 * therefore live in {@code TransferService}; everything that can be decided from
 * the resolved ledger rows is decided here.
 */
@Component
public class TransferRequestValidator {

    private static final Set<String> SUPPORTED_CURRENCIES =
            Set.of("TND", "EUR", "USD");

    private final TransactionProperties properties;

    public TransferRequestValidator(TransactionProperties properties) {
        this.properties = properties;
    }

    /**
     * @param requestedByUserId authenticated caller
     * @param admin            {@code true} when the caller holds the ADMIN role
     */
    public TransferValidation validate(TransferRequest request, LedgerAccount sender, LedgerAccount receiver,
                                       String requestedByUserId, boolean admin) {
        requireOwnership(sender, requestedByUserId, admin);
        requireActive(sender);
        requireActive(receiver);
        BigDecimal amount = requireAmountBounds(request.amount());
        requireSufficientFunds(sender, amount);
        requireDistinctAccounts(sender, receiver);
        String currency = requireCurrency(request.currency(), sender, receiver);
        return new TransferValidation(amount, currency, Money.ZERO);
    }

    /** Rule 3: only the holder may spend from an account; administrators may act for anyone. */
    private void requireOwnership(LedgerAccount sender, String requestedByUserId, boolean admin) {
        if (admin) {
            return;
        }
        if (requestedByUserId == null || !requestedByUserId.equals(sender.getUserId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "You are not allowed to transfer from this account.");
        }
    }

    /** Rule 5. */
    private void requireActive(LedgerAccount account) {
        if (!com.finova.common.domain.AccountStatus.ACTIVE.name().equals(account.getStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVE,
                    ErrorCode.ACCOUNT_NOT_ACTIVE.defaultMessage());
        }
    }

    /** Rule 6: strictly positive, then the lower and the upper transfer ceiling. */
    private BigDecimal requireAmountBounds(BigDecimal rawAmount) {
        if (rawAmount == null || rawAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Amount must be greater than zero.");
        }
        BigDecimal amount = Money.scale(rawAmount);
        if (amount.compareTo(Money.scale(properties.getMinAmount())) < 0) {
            throw new BusinessException(ErrorCode.AMOUNT_BELOW_MINIMUM,
                    "The minimum transfer amount is " + Money.scale(properties.getMinAmount()) + ".");
        }
        if (amount.compareTo(Money.scale(properties.getMaxAmount())) > 0) {
            throw new BusinessException(ErrorCode.AMOUNT_ABOVE_MAXIMUM,
                    "The maximum transfer amount is " + Money.scale(properties.getMaxAmount()) + ".");
        }
        return amount;
    }

    /** Rule 7. */
    private void requireSufficientFunds(LedgerAccount sender, BigDecimal amount) {
        if (sender.getBalance() == null || sender.getBalance().compareTo(amount) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE,
                    ErrorCode.INSUFFICIENT_BALANCE.defaultMessage());
        }
    }

    /** Rule 8: comparing every identifier, so a re-typed number cannot slip through. */
    private void requireDistinctAccounts(LedgerAccount sender, LedgerAccount receiver) {
        boolean sameLedgerRow = sender.getId() != null && sender.getId().equals(receiver.getId());
        boolean sameAccount = sender.getAccountId().equals(receiver.getAccountId());
        boolean sameNumber = sender.getAccountNumber() != null
                && sender.getAccountNumber().equals(receiver.getAccountNumber());
        if (sameLedgerRow || sameAccount || sameNumber) {
            throw new BusinessException(ErrorCode.SENDER_RECEIVER_IDENTICAL,
                    ErrorCode.SENDER_RECEIVER_IDENTICAL.defaultMessage());
        }
    }

    /** Rule 9: supported currency first, then agreement between both legs. */
    private String requireCurrency(String rawCurrency, LedgerAccount sender, LedgerAccount receiver) {
        String currency = rawCurrency == null ? "" : rawCurrency.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_CURRENCIES.contains(currency)) {
            throw new BusinessException(ErrorCode.CURRENCY_NOT_SUPPORTED,
                    ErrorCode.CURRENCY_NOT_SUPPORTED.defaultMessage());
        }
        if (!currency.equals(sender.getCurrency()) || !currency.equals(receiver.getCurrency())) {
            throw new BusinessException(ErrorCode.SAME_CURRENCY_REQUIRED,
                    ErrorCode.SAME_CURRENCY_REQUIRED.defaultMessage());
        }
        return currency;
    }
}