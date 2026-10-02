package com.finova.transaction.service;

import com.finova.common.domain.AccountStatus;
import com.finova.common.error.BusinessException;
import com.finova.common.error.ErrorCode;
import com.finova.common.support.Money;
import com.finova.transaction.config.TransactionProperties;
import com.finova.transaction.domain.LedgerAccount;
import com.finova.transaction.dto.TransferRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.finova.transaction.support.TestFixtures.ACTIVE;
import static com.finova.transaction.support.TestFixtures.BLOCKED;
import static com.finova.transaction.support.TestFixtures.TND;
import static com.finova.transaction.support.TestFixtures.ledgerAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("TransferRequestValidator - the ten mandated rules and their ErrorCode")
class TransferRequestValidatorTest {

    private static final String OWNER = "user-owner";
    private static final String OTHER = "user-other";

    private TransferRequestValidator validator;
    private LedgerAccount sender;
    private LedgerAccount receiver;

    @BeforeEach
    void setUp() {
        validator = new TransferRequestValidator(new TransactionProperties());
        sender = ledgerAccount("acct-sender", "TN5800000000000001", OWNER, ACTIVE, "5000.000");
        receiver = ledgerAccount("acct-receiver", "TN5800000000000002", OTHER, ACTIVE, "1000.000");
    }

    private TransferRequest request(String amount, String currency) {
        return new TransferRequest("acct-sender", "TN5800000000000002", new BigDecimal(amount), currency,
                "Monthly payment");
    }

    private ErrorCode codeOf(Runnable action) {
        BusinessException exception = assertThrows(BusinessException.class, action::run);
        return exception.getErrorCode();
    }

    @Test
    void shouldAcceptAValidTransferAndScaleTheAmount() {
        TransferValidation validation = validator.validate(request("250.00", TND), sender, receiver, OWNER, false);

        assertEquals(Money.scale(new BigDecimal("250.00")), validation.amount());
        assertEquals(TND, validation.currency());
        assertEquals(Money.ZERO, validation.fee());
    }

    @Test
    void shouldRejectAccessDeniedWhenSenderIsNotOwnedByCaller() {
        assertEquals(ErrorCode.ACCESS_DENIED,
                codeOf(() -> validator.validate(request("100", TND), sender, receiver, OTHER, false)));
    }

    @Test
    void shouldAllowAdminToSendFromAnyAccount() {
        TransferValidation validation = validator.validate(request("100", TND), sender, receiver, OTHER, true);

        assertEquals(Money.scale(new BigDecimal("100")), validation.amount());
    }

    @Test
    void shouldRejectAccountNotActiveWhenSenderIsBlocked() {
        sender.setStatus(BLOCKED);

        assertEquals(ErrorCode.ACCOUNT_NOT_ACTIVE,
                codeOf(() -> validator.validate(request("100", TND), sender, receiver, OWNER, false)));
    }

    @Test
    void shouldRejectAccountNotActiveWhenReceiverIsBlocked() {
        receiver.setStatus(BLOCKED);

        assertEquals(ErrorCode.ACCOUNT_NOT_ACTIVE,
                codeOf(() -> validator.validate(request("100", TND), sender, receiver, OWNER, false)));
    }

    @Test
    void shouldRejectAccountNotActiveWhenSenderIsClosed() {
        sender.setStatus(AccountStatus.CLOSED.name());

        assertEquals(ErrorCode.ACCOUNT_NOT_ACTIVE,
                codeOf(() -> validator.validate(request("100", TND), sender, receiver, OWNER, false)));
    }

    @Test
    void shouldRejectValidationErrorForZeroAmount() {
        assertEquals(ErrorCode.VALIDATION_ERROR,
                codeOf(() -> validator.validate(request("0", TND), sender, receiver, OWNER, false)));
    }

    @Test
    void shouldRejectValidationErrorForNegativeAmount() {
        assertEquals(ErrorCode.VALIDATION_ERROR,
                codeOf(() -> validator.validate(request("-25.50", TND), sender, receiver, OWNER, false)));
    }

    @Test
    void shouldRejectAmountBelowMinimum() {
        assertEquals(ErrorCode.AMOUNT_BELOW_MINIMUM,
                codeOf(() -> validator.validate(request("0.0005", TND), sender, receiver, OWNER, false)));
    }

    @Test
    void shouldRejectAmountAboveMaximum() {
        assertEquals(ErrorCode.AMOUNT_ABOVE_MAXIMUM,
                codeOf(() -> validator.validate(request("1000000.001", TND), sender, receiver, OWNER, false)));
    }

    @Test
    void shouldAcceptExactlyTheConfiguredCeiling() {
        sender.setBalance(new BigDecimal("2000000.000"));

        TransferValidation validation = validator.validate(request("1000000.000", TND), sender, receiver, OWNER,
                true);

        assertEquals(Money.scale(new BigDecimal("1000000.000")), validation.amount());
    }

    @Test
    void shouldRejectInsufficientBalance() {
        sender.setBalance(new BigDecimal("100.000"));

        assertEquals(ErrorCode.INSUFFICIENT_BALANCE,
                codeOf(() -> validator.validate(request("250.000", TND), sender, receiver, OWNER, false)));
    }

    @Test
    void shouldRejectSenderAndReceiverIdentical() {
        LedgerAccount sameAsSender = ledgerAccount("acct-sender", "TN5800000000000001", OWNER, ACTIVE, "5000.000");

        assertEquals(ErrorCode.SENDER_RECEIVER_IDENTICAL,
                codeOf(() -> validator.validate(request("100", TND), sameAsSender, sender, OWNER, false)));
    }

    /**
     * The directory rejects a customer transferring to their own account with a
     * 400, which {@code LedgerProjectionService} already remaps. An ADMIN does not
     * trip that guard - the directory has no reason to refuse them - so the
     * validator is the layer that has to catch it.
     */
    @Test
    void shouldRejectSenderAndReceiverIdenticalForAnAdminSendingToTheirOwnAccount() {
        LedgerAccount receiverResolvedByLookup = ledgerAccount("acct-sender", "TN5800000000000001", OWNER, ACTIVE,
                "5000.000");

        assertEquals(ErrorCode.SENDER_RECEIVER_IDENTICAL,
                codeOf(() -> validator.validate(request("100", TND), sender, receiverResolvedByLookup,
                        OWNER, true)));
    }

    @Test
    void shouldRejectSenderAndReceiverIdenticalWhenOnlyTheAccountNumbersMatch() {
        receiver.setAccountId("acct-different-id");
        receiver.setAccountNumber(sender.getAccountNumber());

        assertEquals(ErrorCode.SENDER_RECEIVER_IDENTICAL,
                codeOf(() -> validator.validate(request("100", TND), sender, receiver, OWNER, true)));
    }

    @Test
    void shouldRejectCurrencyNotSupported() {
        assertEquals(ErrorCode.CURRENCY_NOT_SUPPORTED,
                codeOf(() -> validator.validate(request("100", "GBP"), sender, receiver, OWNER, false)));
    }

    @Test
    void shouldRejectSameCurrencyRequiredWhenSenderCurrencyDiffers() {
        receiver.setCurrency("EUR");

        assertEquals(ErrorCode.SAME_CURRENCY_REQUIRED,
                codeOf(() -> validator.validate(request("100", TND), sender, receiver, OWNER, false)));
    }

    @Test
    void shouldRejectSameCurrencyRequiredWhenReceiverCurrencyDiffers() {
        receiver.setCurrency("EUR");

        assertEquals(ErrorCode.SAME_CURRENCY_REQUIRED,
                codeOf(() -> validator.validate(request("100", "EUR"), sender, receiver, OWNER, false)));
    }

    @Test
    void shouldApplyOwnershipBeforeAccountStatus() {
        sender.setStatus(BLOCKED);

        assertEquals(ErrorCode.ACCESS_DENIED,
                codeOf(() -> validator.validate(request("100", TND), sender, receiver, OTHER, false)));
    }

    @Test
    void shouldApplyAccountStatusBeforeAmountBounds() {
        receiver.setStatus(BLOCKED);

        assertEquals(ErrorCode.ACCOUNT_NOT_ACTIVE,
                codeOf(() -> validator.validate(request("0", TND), sender, receiver, OWNER, false)));
    }

    @Test
    void shouldApplyBalanceBeforeCurrencyChecks() {
        sender.setBalance(new BigDecimal("1.000"));

        assertEquals(ErrorCode.INSUFFICIENT_BALANCE,
                codeOf(() -> validator.validate(request("250", "GBP"), sender, receiver, OWNER, false)));
    }

    @Test
    void shouldApplyDistinctAccountsBeforeCurrencyChecks() {
        LedgerAccount sameAsSender = ledgerAccount("acct-sender", "TN5800000000000001", OWNER, ACTIVE, "5000.000");

        assertEquals(ErrorCode.SENDER_RECEIVER_IDENTICAL,
                codeOf(() -> validator.validate(request("100", "GBP"), sameAsSender, sender, OWNER, false)));
    }
}