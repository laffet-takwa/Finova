package com.finova.transaction.service;

import com.finova.common.error.BusinessException;
import com.finova.common.error.ErrorCode;
import com.finova.transaction.domain.LedgerAccount;
import com.finova.transaction.domain.Transaction;
import com.finova.transaction.dto.TransferOutcome;
import com.finova.transaction.dto.TransferRequest;
import com.finova.transaction.mapper.AccountNumbers;
import com.finova.transaction.mapper.TransactionMapper;
import com.finova.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static com.finova.transaction.support.TestFixtures.ACTIVE;
import static com.finova.transaction.support.TestFixtures.BLOCKED;
import static com.finova.transaction.support.TestFixtures.TND;
import static com.finova.transaction.support.TestFixtures.ledgerAccount;
import static com.finova.transaction.support.TestFixtures.transaction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("TransferService - validation order, idempotency and ownership")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransferServiceTest {

    private static final String OWNER = "user-owner";
    private static final String KEY = "8f14e45f-ea5e-4c3f-9a6f-3b7d2c1e0a94";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-01T09:15:00Z"), ZoneOffset.UTC);

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private TransferRequestValidator validator;
    @Mock
    private LedgerProjectionService projections;
    @Mock
    private TransferPersistence persistence;

    private RequestFingerprint fingerprint;
    private TransactionMapper mapper;
    private TransferService service;
    private LedgerAccount sender;
    private LedgerAccount receiver;

    @BeforeEach
    void setUp() {
        fingerprint = new RequestFingerprint();
        mapper = new TransactionMapper(new AccountNumbers());
        service = new TransferService(transactionRepository, validator, projections, fingerprint, persistence, mapper);
        sender = ledgerAccount("acct-sender", "TN5800000000000001", OWNER, ACTIVE, "5000.000");
        receiver = ledgerAccount("acct-receiver", "TN5800000000000002", "user-receiver", ACTIVE, "100.000");
        when(projections.resolveByAccountId("acct-sender")).thenReturn(sender);
        when(projections.resolveByAccountNumber("TN5800000000000002")).thenReturn(receiver);
        when(validator.validate(any(), any(), any(), anyString(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(new TransferValidation(new BigDecimal("250.000"), TND, BigDecimal.ZERO));
    }

    private TransferRequest request(String amount) {
        return new TransferRequest("acct-sender", "TN5800000000000002", new BigDecimal(amount), TND,
                "Monthly payment");
    }

    private ErrorCode codeOf(Runnable action) {
        BusinessException exception = assertThrows(BusinessException.class, action::run);
        return exception.getErrorCode();
    }

    @Test
    void shouldCreatePendingTransferWhenRequestIsValid() {
        TransferRequest request = request("250.00");
        when(transactionRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());
        when(persistence.insert(any(), anyString(), anyString(), any(), any(), any(), anyString(), any()))
                .thenReturn(transaction("tx-1", "TX-20261001-00001", "acct-sender", "acct-receiver", "250.000",
                        "PENDING"));

        TransferOutcome outcome = service.create(request, KEY, OWNER, false, "10.0.0.1");

        assertFalse(outcome.replay());
        assertEquals("PENDING", outcome.response().status());
        assertEquals("TX-20261001-00001", outcome.response().reference());
    }

    @Test
    void shouldReturnOriginalTransactionAsReplayWhenKeyAndPayloadMatch() {
        TransferRequest request = request("250.00");
        Transaction existing = transaction("tx-1", "TX-20261001-00001", "acct-sender", "acct-receiver", "250.000",
                "PENDING");
        existing.setRequestFingerprint(fingerprint.of(request));
        when(transactionRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(existing));

        TransferOutcome outcome = service.create(request, KEY, OWNER, false, "10.0.0.1");

        assertTrue(outcome.replay());
        assertEquals("tx-1", outcome.response().id());
        verify(persistence, never()).insert(any(), anyString(), anyString(), any(), any(), any(), anyString(), any());
    }

    @Test
    void shouldRejectIdempotencyKeyReusedWhenPayloadDiffers() {
        Transaction existing = transaction("tx-1", "TX-20261001-00001", "acct-sender", "acct-receiver", "250.000",
                "PENDING");
        existing.setRequestFingerprint("a".repeat(64));
        when(transactionRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(existing));

        assertEquals(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                codeOf(() -> service.create(request("250.00"), KEY, OWNER, false, "10.0.0.1")));
    }

    @Test
    void shouldCheckReplayBeforeOwnershipSoARealRetrySucceeds() {
        TransferRequest request = request("250.00");
        Transaction existing = transaction("tx-1", "TX-20261001-00001", "acct-sender", "acct-receiver", "250.000",
                "PENDING");
        existing.setRequestFingerprint(fingerprint.of(request));
        when(transactionRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(existing));

        TransferOutcome outcome = service.create(request, KEY, "someone-else", false, "10.0.0.1");

        assertTrue(outcome.replay());
        verify(validator, never()).validate(any(), any(), any(), anyString(),
                org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void shouldResolveConcurrentDuplicateAsReplay() {
        TransferRequest request = request("250.00");
        Transaction winner = transaction("tx-1", "TX-20261001-00001", "acct-sender", "acct-receiver", "250.000",
                "PENDING");
        winner.setRequestFingerprint(fingerprint.of(request));
        when(transactionRepository.findByIdempotencyKey(KEY))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(persistence.insert(any(), anyString(), anyString(), any(), any(), any(), anyString(), any()))
                .thenThrow(new DataIntegrityViolationException("uk_transaction_idempotency_key"));

        TransferOutcome outcome = service.create(request, KEY, OWNER, false, "10.0.0.1");

        assertTrue(outcome.replay());
        assertEquals("tx-1", outcome.response().id());
    }

    @Test
    void shouldRejectMissingIdempotencyKey() {
        assertEquals(ErrorCode.VALIDATION_ERROR,
                codeOf(() -> service.create(request("250.00"), null, OWNER, false, "10.0.0.1")));
    }

    @Test
    void shouldRejectBlankIdempotencyKey() {
        assertEquals(ErrorCode.VALIDATION_ERROR,
                codeOf(() -> service.create(request("250.00"), "   ", OWNER, false, "10.0.0.1")));
    }

    @Test
    void shouldRejectTooShortIdempotencyKey() {
        assertEquals(ErrorCode.VALIDATION_ERROR,
                codeOf(() -> service.create(request("250.00"), "short", OWNER, false, "10.0.0.1")));
    }

    @Test
    void shouldRejectTooLongIdempotencyKey() {
        assertEquals(ErrorCode.VALIDATION_ERROR,
                codeOf(() -> service.create(request("250.00"), "k".repeat(81), OWNER, false, "10.0.0.1")));
    }

    @Test
    void shouldAcceptIdempotencyKeyOfExactlyEightCharacters() {
        when(transactionRepository.findByIdempotencyKey("12345678")).thenReturn(Optional.empty());
        when(persistence.insert(any(), anyString(), anyString(), any(), any(), any(), anyString(), any()))
                .thenReturn(transaction("tx-1", "TX-20261001-00001", "acct-sender", "acct-receiver", "250.000",
                        "PENDING"));

        TransferOutcome outcome = service.create(request("250.00"), "12345678", OWNER, false, "10.0.0.1");

        assertFalse(outcome.replay());
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(persistence).insert(any(), key.capture(), anyString(), any(), any(), any(), anyString(), any());
        assertEquals("12345678", key.getValue());
    }

    @Test
    void shouldSurfaceAccountNotFoundWhenSenderProjectionCannotBeResolved() {
        when(transactionRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());
        when(projections.resolveByAccountId("acct-sender"))
                .thenThrow(new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND,
                codeOf(() -> service.create(request("250.00"), KEY, OWNER, false, "10.0.0.1")));
    }

    @Test
    void shouldSurfaceServiceUnavailableWhenAccountDirectoryIsDown() {
        when(transactionRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());
        when(projections.resolveByAccountId("acct-sender"))
                .thenThrow(new BusinessException(ErrorCode.SERVICE_UNAVAILABLE));

        assertEquals(ErrorCode.SERVICE_UNAVAILABLE,
                codeOf(() -> service.create(request("250.00"), KEY, OWNER, false, "10.0.0.1")));
    }

    @Test
    void shouldSurfaceAccountNotFoundWhenReceiverProjectionCannotBeResolved() {
        when(transactionRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());
        when(projections.resolveByAccountNumber("TN5800000000000002"))
                .thenThrow(new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND,
                codeOf(() -> service.create(request("250.00"), KEY, OWNER, false, "10.0.0.1")));
    }

    @Test
    void shouldSurfaceAccessDeniedWhenSenderIsNotOwnedByCaller() {
        when(transactionRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());
        when(validator.validate(any(), any(), any(), anyString(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenThrow(new BusinessException(ErrorCode.ACCESS_DENIED));

        assertEquals(ErrorCode.ACCESS_DENIED,
                codeOf(() -> service.create(request("250.00"), KEY, "user-attacker", false, "10.0.0.1")));
    }

    @Test
    void shouldLetAdminSendFromAnyAccount() {
        when(transactionRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());
        when(persistence.insert(any(), anyString(), anyString(), any(), any(), any(), anyString(), any()))
                .thenReturn(transaction("tx-1", "TX-20261001-00001", "acct-sender", "acct-receiver", "250.000",
                        "PENDING"));

        TransferOutcome outcome = service.create(request("250.00"), KEY, "admin-1", true, "10.0.0.1");

        assertFalse(outcome.replay());
        verify(validator).validate(any(), any(), any(), org.mockito.ArgumentMatchers.eq("admin-1"),
                org.mockito.ArgumentMatchers.eq(true));
    }

    @Test
    void shouldSurfaceAccountNotActiveWhenSenderIsBlocked() {
        sender.setStatus(BLOCKED);
        when(transactionRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());
        when(validator.validate(any(), any(), any(), anyString(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenThrow(new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVE));

        assertEquals(ErrorCode.ACCOUNT_NOT_ACTIVE,
                codeOf(() -> service.create(request("250.00"), KEY, OWNER, false, "10.0.0.1")));
    }

    @Test
    void shouldPropagateInsufficientBalance() {
        when(transactionRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());
        when(validator.validate(any(), any(), any(), anyString(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenThrow(new BusinessException(ErrorCode.INSUFFICIENT_BALANCE));

        assertEquals(ErrorCode.INSUFFICIENT_BALANCE,
                codeOf(() -> service.create(request("250.00"), KEY, OWNER, false, "10.0.0.1")));
    }

    @Test
    void shouldPropagateCurrencyNotSupported() {
        when(transactionRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());
        when(validator.validate(any(), any(), any(), anyString(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenThrow(new BusinessException(ErrorCode.CURRENCY_NOT_SUPPORTED));

        assertEquals(ErrorCode.CURRENCY_NOT_SUPPORTED,
                codeOf(() -> service.create(request("250.00", "GBP"), KEY, OWNER, false, "10.0.0.1")));
    }

    @Test
    void shouldPropagateSenderReceiverIdentical() {
        when(transactionRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());
        when(validator.validate(any(), any(), any(), anyString(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenThrow(new BusinessException(ErrorCode.SENDER_RECEIVER_IDENTICAL));

        assertEquals(ErrorCode.SENDER_RECEIVER_IDENTICAL,
                codeOf(() -> service.create(request("250.00"), KEY, OWNER, false, "10.0.0.1")));
    }

    @Test
    void shouldPropagateAmountBounds() {
        when(transactionRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());
        when(validator.validate(any(), any(), any(), anyString(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenThrow(new BusinessException(ErrorCode.AMOUNT_ABOVE_MAXIMUM));

        assertEquals(ErrorCode.AMOUNT_ABOVE_MAXIMUM,
                codeOf(() -> service.create(request("2000000.00"), KEY, OWNER, false, "10.0.0.1")));
    }

    @Test
    void shouldTrimTheIdempotencyKeyBeforePersisting() {
        when(transactionRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());
        when(persistence.insert(any(), anyString(), anyString(), any(), any(), any(), anyString(), any()))
                .thenReturn(transaction("tx-1", "TX-20261001-00001", "acct-sender", "acct-receiver", "250.000",
                        "PENDING"));

        service.create(request("250.00"), "  " + KEY + "  ", OWNER, false, "10.0.0.1");

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(persistence).insert(any(), key.capture(), anyString(), any(), any(), any(), anyString(), any());
        assertEquals(KEY, key.getValue());
    }

    @Test
    void shouldPassCallerAndAdminFlagToTheValidator() {
        when(transactionRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());
        when(persistence.insert(any(), anyString(), anyString(), any(), any(), any(), anyString(), any()))
                .thenReturn(transaction("tx-1", "TX-20261001-00001", "acct-sender", "acct-receiver", "250.000",
                        "PENDING"));

        service.create(request("250.00"), KEY, OWNER, false, "10.0.0.1");

        verify(validator).validate(any(), org.mockito.ArgumentMatchers.same(sender),
                org.mockito.ArgumentMatchers.same(receiver), org.mockito.ArgumentMatchers.eq(OWNER),
                org.mockito.ArgumentMatchers.eq(false));
    }

    private TransferRequest request(String amount, String currency) {
        return new TransferRequest("acct-sender", "TN5800000000000002", new BigDecimal(amount), currency,
                "Monthly payment");
    }
}