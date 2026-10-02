package com.finova.account.service;

import com.finova.account.domain.Account;
import com.finova.account.domain.BalanceProjection;
import com.finova.account.repository.AccountRepository;
import com.finova.account.repository.BalanceProjectionRepository;
import com.finova.account.support.AccountFixtures;
import com.finova.common.domain.AccountStatus;
import com.finova.common.event.TransactionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountProjectionServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private BalanceProjectionRepository balanceProjectionRepository;

    @Mock
    private SettlementAccountResolver settlementAccountResolver;

    private AccountProjectionService projectionService;

    @BeforeEach
    void setUp() {
        projectionService = new AccountProjectionService(accountRepository, balanceProjectionRepository,
            settlementAccountResolver);
    }

    @Test
    void shouldCreditTheReceiverAndDebitTheSender() {
        Account sender = AccountFixtures.account("sender", "user-1", AccountStatus.ACTIVE, new BigDecimal("1000.000"));
        Account receiver = AccountFixtures.account("receiver", "user-2", "EU58200003456789012345",
            com.finova.common.domain.AccountType.SAVINGS, com.finova.common.domain.Currency.EUR,
            new BigDecimal("100.000"), AccountStatus.ACTIVE);
        when(balanceProjectionRepository.existsById("tx-1")).thenReturn(false);
        when(accountRepository.findById("sender")).thenReturn(Optional.of(sender));
        when(accountRepository.findById("receiver")).thenReturn(Optional.of(receiver));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        boolean applied = projectionService.applyCompleted(transaction("tx-1", "100.000"));

        assertTrue(applied);
        assertEquals(new BigDecimal("900.000"), sender.getBalance());
        assertEquals(new BigDecimal("200.000"), receiver.getBalance());
        verify(balanceProjectionRepository).save(any(BalanceProjection.class));
    }

    @Test
    void shouldSkipAnAlreadyAppliedTransaction() {
        when(balanceProjectionRepository.existsById("tx-1")).thenReturn(true);

        boolean applied = projectionService.applyCompleted(transaction("tx-1", "100.000"));

        assertFalse(applied);
        verify(accountRepository, never()).save(any(Account.class));
        verify(balanceProjectionRepository, never()).save(any(BalanceProjection.class));
    }

    @Test
    void shouldResolveTheAccountByNumberWhenTheEventCarriesNoAccountId() {
        Account sender = AccountFixtures.account("sender", "user-1", AccountStatus.ACTIVE, new BigDecimal("50.000"));
        when(balanceProjectionRepository.existsById("tx-1")).thenReturn(false);
        when(accountRepository.findById("sender")).thenReturn(Optional.of(sender));
        when(settlementAccountResolver.findByAccountNumber(AccountFixtures.ACCOUNT_NUMBER))
            .thenReturn(Optional.of(AccountFixtures.account("receiver", "user-2", AccountStatus.ACTIVE,
                new BigDecimal("0.000"))));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        projectionService.applyCompleted(transactionWithoutReceiverAccountId("tx-1", "25.000"));

        assertEquals(new BigDecimal("25.000"), sender.getBalance());
        verify(settlementAccountResolver).findByAccountNumber(AccountFixtures.ACCOUNT_NUMBER);
    }

    @Test
    void shouldRecordTheTransactionIdAsTheIdempotencyMarker() {
        when(balanceProjectionRepository.existsById("tx-9")).thenReturn(false);
        when(accountRepository.findById("sender")).thenReturn(Optional.of(
            AccountFixtures.account("sender", "user-1", AccountStatus.ACTIVE, new BigDecimal("10.000"))));
        when(accountRepository.findById("receiver")).thenReturn(Optional.of(
            AccountFixtures.account("receiver", "user-2", AccountStatus.ACTIVE, new BigDecimal("0.000"))));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        projectionService.applyCompleted(transaction("tx-9", "5.000"));

        ArgumentCaptor<BalanceProjection> marker = ArgumentCaptor.forClass(BalanceProjection.class);
        verify(balanceProjectionRepository).save(marker.capture());
        assertEquals("tx-9", marker.getValue().getTransactionId());
        assertTrue(marker.getValue().getAppliedAt().isAfter(Instant.now().minusSeconds(30)));
    }

    @Test
    void shouldIgnoreAFailedTransaction() {
        projectionService.onTransactionFailed(transaction("tx-2", "10.000"));

        verify(balanceProjectionRepository, never()).save(any(BalanceProjection.class));
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void shouldDiscardAnEventWithoutATransactionId() {
        assertFalse(projectionService.applyCompleted(transaction(null, "10.000")));
        assertFalse(projectionService.applyCompleted(null));
    }

    @Test
    void shouldClampTheProjectionInsteadOfFailingWhenTheSenderWouldGoNegative() {
        Account sender = AccountFixtures.account("sender", "user-1", AccountStatus.ACTIVE, new BigDecimal("10.000"));
        when(balanceProjectionRepository.existsById("tx-1")).thenReturn(false);
        when(accountRepository.findById("sender")).thenReturn(Optional.of(sender));
        when(accountRepository.findById("receiver")).thenReturn(Optional.of(
            AccountFixtures.account("receiver", "user-2", AccountStatus.ACTIVE, new BigDecimal("0.000"))));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        projectionService.applyCompleted(transaction("tx-1", "999.000"));

        assertEquals(new BigDecimal("0.000"), sender.getBalance());
    }

    private static TransactionEvent transaction(String transactionId, String amount) {
        return new TransactionEvent(transactionId, "TX-20260101-00001", "sender", "receiver",
            "TN58100001234567890199", AccountFixtures.ACCOUNT_NUMBER, "user-1", "user-2",
            new BigDecimal(amount), "TND", "Transfer", "TRANSFER", "COMPLETED",
            10, "LOW", List.of(), null, "user-1", "127.0.0.1", Instant.now());
    }

    private static TransactionEvent transactionWithoutReceiverAccountId(String transactionId, String amount) {
        return new TransactionEvent(transactionId, "TX-20260101-00002", "sender", null,
            "TN58100001234567890199", AccountFixtures.ACCOUNT_NUMBER, "user-1", "user-2",
            new BigDecimal(amount), "TND", "Transfer", "TRANSFER", "COMPLETED",
            10, "LOW", List.of(), null, "user-1", "127.0.0.1", Instant.now());
    }
}
