package com.finova.transaction.service;

import com.finova.common.domain.TransactionStatus;
import com.finova.common.event.EventType;
import com.finova.common.event.Topics;
import com.finova.transaction.config.TransactionProperties;
import com.finova.transaction.domain.Transaction;
import com.finova.transaction.event.OutboxService;
import com.finova.transaction.event.TransactionEventFactory;
import com.finova.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static com.finova.transaction.support.TestFixtures.transaction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("PendingTransactionReconciler - re-drives only genuinely stale transfers")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PendingTransactionReconcilerTest {

    private static final Instant NOW = Instant.parse("2026-10-01T09:15:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private TransactionRepository repository;
    @Mock
    private OutboxService outbox;

    private PendingTransactionReconciler reconciler;
    private TransactionProperties properties;

    @BeforeEach
    void setUp() {
        properties = new TransactionProperties();
        properties.setReconcileThresholdSeconds(30);
        reconciler = new PendingTransactionReconciler(repository, outbox, new TransactionEventFactory(), properties,
                CLOCK);
    }

    private Transaction pendingCreatedAt(Instant createdAt) {
        Transaction transaction = transaction("tx-" + createdAt.toEpochMilli(), "TX-20261001-00001", "acct-sender",
                "acct-receiver", "250.000", TransactionStatus.PENDING.name());
        transaction.setCreatedAt(createdAt);
        return transaction;
    }

    @Test
    void shouldRepublishAStalePendingTransaction() {
        Transaction stale = pendingCreatedAt(NOW.minusSeconds(120));
        when(repository.findByStatus(eq(TransactionStatus.PENDING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(stale)));

        reconciler.republishStaleTransactions();

        verify(outbox).enqueue(eq(Topics.TRANSACTION_CREATED), eq(stale.getId()),
                eq(EventType.TRANSACTION_CREATED), any());
        verify(outbox).enqueue(eq(Topics.AUDIT_RECORDED), eq(stale.getId()), eq(EventType.AUDIT_RECORDED), any());
    }

    @Test
    void shouldNotRepublishAFreshPendingTransaction() {
        Transaction fresh = pendingCreatedAt(NOW.minusSeconds(5));
        when(repository.findByStatus(eq(TransactionStatus.PENDING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(fresh)));

        reconciler.republishStaleTransactions();

        verify(outbox, never()).enqueue(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), any(), any());
    }

    @Test
    void shouldNotRepublishWhenNothingIsStale() {
        when(repository.findByStatus(eq(TransactionStatus.PENDING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        reconciler.republishStaleTransactions();

        verify(outbox, never()).enqueue(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), any(), any());
    }

    @Test
    void shouldComputeTheThresholdFromTheConfiguredSeconds() {
        assertEquals(NOW.minusSeconds(30), reconciler.staleBefore(NOW));

        properties.setReconcileThresholdSeconds(300);
        assertEquals(NOW.minusSeconds(300), reconciler.staleBefore(NOW));
    }

    @Test
    void shouldTreatExactlyTheThresholdAsNotYetStale() {
        when(repository.findByStatus(eq(TransactionStatus.PENDING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(pendingCreatedAt(reconciler.staleBefore(NOW)))));

        assertTrue(reconciler.stalePending(reconciler.staleBefore(NOW), 100).isEmpty(),
                "a transfer exactly at the threshold is not stale yet");
    }

    @Test
    void shouldTreatOneSecondPastTheThresholdAsStale() {
        when(repository.findByStatus(eq(TransactionStatus.PENDING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(pendingCreatedAt(NOW.minusSeconds(31)))));

        assertEquals(1, reconciler.stalePending(reconciler.staleBefore(NOW), 100).size());
    }

    @Test
    void shouldOnlyRepublishTransactionsTheQueryReturned() {
        Transaction stale = pendingCreatedAt(NOW.minusSeconds(600));
        when(repository.findByStatus(eq(TransactionStatus.PENDING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(stale)));

        reconciler.republishStaleTransactions();

        verify(repository).findByStatus(eq(TransactionStatus.PENDING), ArgumentMatchers.any(Pageable.class));
        verify(outbox, times(2)).enqueue(ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(), any(), any());
    }
}