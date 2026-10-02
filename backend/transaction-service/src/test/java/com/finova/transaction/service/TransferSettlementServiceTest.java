package com.finova.transaction.service;

import com.finova.common.domain.AccountStatus;
import com.finova.common.domain.TransactionStatus;
import com.finova.common.event.EventType;
import com.finova.common.event.Topics;
import com.finova.transaction.domain.LedgerAccount;
import com.finova.transaction.domain.LedgerEvent;
import com.finova.transaction.domain.Transaction;
import com.finova.transaction.domain.TransactionEventMarker;
import com.finova.transaction.event.OutboxService;
import com.finova.transaction.event.TransactionEventFactory;
import com.finova.transaction.repository.LedgerAccountRepository;
import com.finova.transaction.repository.LedgerEventRepository;
import com.finova.transaction.repository.TransactionEventMarkerRepository;
import com.finova.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static com.finova.transaction.support.TestFixtures.ACTIVE;
import static com.finova.transaction.support.TestFixtures.BLOCKED;
import static com.finova.transaction.support.TestFixtures.TND;
import static com.finova.transaction.support.TestFixtures.ledgerAccount;
import static com.finova.transaction.support.TestFixtures.transaction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("TransferSettlementService - authoritative, idempotent, double entry")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransferSettlementServiceTest {

    private static final String TOPIC = Topics.TRANSACTION_APPROVED;
    private static final String EVENT_ID = "evt-1";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-01T09:15:00Z"), ZoneOffset.UTC);

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private LedgerAccountRepository ledgerRepository;
    @Mock
    private LedgerEventRepository ledgerEventRepository;
    @Mock
    private TransactionEventMarkerRepository markerRepository;
    @Mock
    private OutboxService outbox;

    private TransferSettlementService settlementService;
    private TransactionEventFactory events;
    private LedgerAccount sender;
    private LedgerAccount receiver;

    @BeforeEach
    void setUp() {
        events = new TransactionEventFactory();
        settlementService = new TransferSettlementService(transactionRepository, ledgerRepository,
                ledgerEventRepository, markerRepository, events, outbox, CLOCK);
        sender = ledgerAccount("acct-sender", "TN5800000000000001", "user-sender", ACTIVE, "1000.000");
        receiver = ledgerAccount("acct-receiver", "TN5800000000000002", "user-receiver", ACTIVE, "50.000");
        sender.setId("00000000-0000-0000-0000-000000000001");
        receiver.setId("00000000-0000-0000-0000-000000000002");
    }

    private Transaction pendingTransaction(String amount) {
        Transaction pending = transaction("tx-1", "TX-20261001-00001", "acct-sender", "acct-receiver", amount,
                TransactionStatus.PENDING.name());
        return pending;
    }

    private void stubLockedPair(Transaction pending) {
        when(markerRepository.existsByTopicAndEventId(TOPIC, EVENT_ID)).thenReturn(false);
        when(transactionRepository.findByIdForUpdate(pending.getId())).thenReturn(Optional.of(pending));
when(ledgerRepository.findIdsByAccountIdIn(any()))
                .thenReturn(List.of(sender.getId(), receiver.getId()));
        when(ledgerRepository.findByIdForUpdate(sender.getId())).thenReturn(Optional.of(sender));
        when(ledgerRepository.findByIdForUpdate(receiver.getId())).thenReturn(Optional.of(receiver));
    }

    @Test
    void shouldDebitSenderCreditReceiverAndWriteBothLedgerLegs() {
        Transaction pending = pendingTransaction("250.000");
        stubLockedPair(pending);

        settlementService.settle(pending.getId(), TOPIC, EVENT_ID);

        assertMoney("750.000", sender.getBalance());
        assertMoney("300.000", receiver.getBalance());
        assertEquals(TransactionStatus.COMPLETED.name(), pending.getStatus());
        assertNotNull(pending.getCompletedAt());
        assertMoney("750.000", pending.getSettledSenderBalance());
        assertMoney("300.000", pending.getSettledReceiverBalance());
        assertNull(pending.getFailureReason());

        ArgumentCaptor<LedgerEvent> captor = ArgumentCaptor.forClass(LedgerEvent.class);
        verify(ledgerEventRepository, times(2)).save(captor.capture());
        List<LedgerEvent> legs = captor.getAllValues();
        LedgerEvent debit = legs.get(0);
        LedgerEvent credit = legs.get(1);
        assertEquals(LedgerEvent.DIRECTION_DEBIT, debit.getDirection());
        assertEquals(sender.getId(), debit.getLedgerAccountId());
        assertMoney("1000.000", debit.getBalanceBefore());
        assertMoney("750.000", debit.getBalanceAfter());
        assertEquals(LedgerEvent.DIRECTION_CREDIT, credit.getDirection());
        assertEquals(receiver.getId(), credit.getLedgerAccountId());
        assertMoney("50.000", credit.getBalanceBefore());
        assertMoney("300.000", credit.getBalanceAfter());
        assertEquals("TX-20261001-00001", debit.getReference());
        assertEquals(pending.getId(), credit.getTransactionId());
    }

    @Test
    void shouldPublishSettlementAndAuditOnTheOutbox() {
        Transaction pending = pendingTransaction("250.000");
        stubLockedPair(pending);

        settlementService.settle(pending.getId(), TOPIC, EVENT_ID);

        verify(outbox).enqueue(eq(Topics.TRANSACTION_COMPLETED),
                eq(pending.getId()),
                eq(EventType.TRANSACTION_COMPLETED), any());
        verify(outbox).enqueue(eq(Topics.AUDIT_RECORDED),
                eq(pending.getId()),
                eq(EventType.AUDIT_RECORDED), any());
    }

    @Test
    void shouldRecordTheConsumerIdempotencyMarker() {
        Transaction pending = pendingTransaction("250.000");
        stubLockedPair(pending);

        settlementService.settle(pending.getId(), TOPIC, EVENT_ID);

        ArgumentCaptor<TransactionEventMarker> captor = ArgumentCaptor.forClass(TransactionEventMarker.class);
        verify(markerRepository).save(captor.capture());
        assertEquals(TOPIC, captor.getValue().getTopic());
        assertEquals(EVENT_ID, captor.getValue().getEventId());
        assertEquals(pending.getId(), captor.getValue().getTransactionId());
        assertNotNull(captor.getValue().getProcessedAt());
    }

    @Test
    void shouldLockBothLedgerRowsInAscendingIdOrder() {
        sender.setId("00000000-0000-0000-0000-000000000009");
        receiver.setId("00000000-0000-0000-0000-000000000003");
        Transaction pending = pendingTransaction("250.000");
        stubLockedPair(pending);

        settlementService.settle(pending.getId(), TOPIC, EVENT_ID);

        InOrder order = inOrder(ledgerRepository);
        order.verify(ledgerRepository).findByIdForUpdate(receiver.getId());
        order.verify(ledgerRepository).findByIdForUpdate(sender.getId());
    }

    @Test
    void shouldBeANoOpWhenTheDecisionWasAlreadyProcessed() {
        when(markerRepository.existsByTopicAndEventId(TOPIC, EVENT_ID)).thenReturn(true);

        settlementService.settle("tx-1", TOPIC, EVENT_ID);

        verify(transactionRepository, never()).findByIdForUpdate(anyString());
        verify(ledgerRepository, never()).findByIdForUpdate(anyString());
        verify(ledgerEventRepository, never()).save(any());
        verify(markerRepository, never()).save(any());
        verify(outbox, never()).enqueue(anyString(), anyString(), any(), any());
    }

    @Test
    void shouldBeANoOpWhenTheTransactionIsAlreadyCompleted() {
        Transaction completed = transaction("tx-1", "TX-20261001-00001", "acct-sender", "acct-receiver", "250.000",
                TransactionStatus.COMPLETED.name());
        completed.setCompletedAt(Instant.parse("2026-10-01T09:10:00Z"));
        when(markerRepository.existsByTopicAndEventId(TOPIC, EVENT_ID)).thenReturn(false);
        when(transactionRepository.findByIdForUpdate(completed.getId())).thenReturn(Optional.of(completed));

        settlementService.settle(completed.getId(), TOPIC, EVENT_ID);

        assertMoney("1000.000", sender.getBalance());
        assertMoney("50.000", receiver.getBalance());
        verify(ledgerRepository, never()).findByIdForUpdate(anyString());
        verify(ledgerEventRepository, never()).save(any());
        verify(outbox, never()).enqueue(anyString(), anyString(), any(), any());
        verify(markerRepository).save(any());
    }

    @Test
    void shouldFailWithoutMovingMoneyWhenTheBalanceVanishedBeforeSettlement() {
        sender.setBalance(new BigDecimal("10.000"));
        Transaction pending = pendingTransaction("250.000");
        stubLockedPair(pending);

        settlementService.settle(pending.getId(), TOPIC, EVENT_ID);

        assertEquals(TransactionStatus.FAILED.name(), pending.getStatus());
        assertTrue(pending.getFailureReason().contains("Insufficient balance"));
        assertMoney("10.000", sender.getBalance());
        assertMoney("50.000", receiver.getBalance());
        assertNull(pending.getSettledSenderBalance());
        assertNull(pending.getSettledReceiverBalance());
        verify(ledgerEventRepository, never()).save(any());
        verify(outbox).enqueue(eq(Topics.TRANSACTION_FAILED),
                eq(pending.getId()),
                eq(EventType.TRANSACTION_FAILED), any());
    }

    @Test
    void shouldFailWhenTheSenderIsBlockedMidFlight() {
        sender.setStatus(BLOCKED);
        Transaction pending = pendingTransaction("250.000");
        stubLockedPair(pending);

        settlementService.settle(pending.getId(), TOPIC, EVENT_ID);

        assertEquals(TransactionStatus.FAILED.name(), pending.getStatus());
        assertTrue(pending.getFailureReason().contains("not active"));
        assertMoney("1000.000", sender.getBalance());
        verify(ledgerEventRepository, never()).save(any());
        verify(outbox).enqueue(eq(Topics.TRANSACTION_FAILED),
                eq(pending.getId()),
                eq(EventType.TRANSACTION_FAILED), any());
    }

    @Test
    void shouldFailWhenTheReceiverIsBlockedMidFlight() {
        receiver.setStatus(AccountStatus.CLOSED.name());
        Transaction pending = pendingTransaction("250.000");
        stubLockedPair(pending);

        settlementService.settle(pending.getId(), TOPIC, EVENT_ID);

        assertEquals(TransactionStatus.FAILED.name(), pending.getStatus());
        assertMoney("1000.000", sender.getBalance());
        assertMoney("50.000", receiver.getBalance());
    }

    @Test
    void shouldFailWhenOneLedgerRowDisappeared() {
        Transaction pending = pendingTransaction("250.000");
        when(markerRepository.existsByTopicAndEventId(TOPIC, EVENT_ID)).thenReturn(false);
        when(transactionRepository.findByIdForUpdate(pending.getId())).thenReturn(Optional.of(pending));
        when(ledgerRepository.findIdsByAccountIdIn(any()))
                .thenReturn(List.of(sender.getAccountId()));

        settlementService.settle(pending.getId(), TOPIC, EVENT_ID);

        assertEquals(TransactionStatus.FAILED.name(), pending.getStatus());
        assertTrue(pending.getFailureReason().contains("no longer exists"));
        verify(ledgerEventRepository, never()).save(any());
    }

    @Test
    void shouldHoldHighRiskTransferForReviewWithoutMovingMoney() {
        Transaction pending = pendingTransaction("2750.000");
        when(markerRepository.existsByTopicAndEventId(Topics.TRANSACTION_FLAGGED, "evt-flag"))
                .thenReturn(false);
        when(transactionRepository.findByIdForUpdate(pending.getId())).thenReturn(Optional.of(pending));

        settlementService.holdForReview(new SettlementDecision(pending.getId(), 88, "HIGH",
                List.of("Unusual amount", "First transfer to this beneficiary")),
                Topics.TRANSACTION_FLAGGED, "evt-flag");

        assertEquals(TransactionStatus.FLAGGED.name(), pending.getStatus());
        assertEquals(88, pending.getRiskScore());
        assertEquals("HIGH", pending.getRiskLevel());
        assertEquals(List.of("Unusual amount", "First transfer to this beneficiary"), pending.getRiskReasons());
        assertMoney("1000.000", sender.getBalance());
        assertMoney("50.000", receiver.getBalance());
        assertNull(pending.getCompletedAt());
        verify(ledgerRepository, never()).findByIdForUpdate(anyString());
        verify(ledgerEventRepository, never()).save(any());
        verify(markerRepository).save(any());
        verify(outbox).enqueue(eq(Topics.AUDIT_RECORDED),
                eq(pending.getId()),
                eq(EventType.AUDIT_RECORDED), any());
    }

    @Test
    void shouldIgnoreARedeliveredFlaggedDecision() {
        when(markerRepository.existsByTopicAndEventId(Topics.TRANSACTION_FLAGGED, "evt-flag")).thenReturn(true);

        settlementService.holdForReview(new SettlementDecision("tx-1", 88, "HIGH", List.of()),
                Topics.TRANSACTION_FLAGGED, "evt-flag");

        verify(transactionRepository, never()).findByIdForUpdate(anyString());
        verify(markerRepository, never()).save(any());
    }

    @Test
    void shouldIgnoreARedeliveredApprovalForATransactionThatIsAlreadyFlagged() {
        Transaction flagged = transaction("tx-1", "TX-20261001-00001", "acct-sender", "acct-receiver", "2750.000",
                TransactionStatus.FLAGGED.name());
        when(markerRepository.existsByTopicAndEventId(TOPIC, EVENT_ID)).thenReturn(false);
        when(transactionRepository.findByIdForUpdate(flagged.getId())).thenReturn(Optional.of(flagged));

        settlementService.settle(flagged.getId(), TOPIC, EVENT_ID);

        assertEquals(TransactionStatus.FLAGGED.name(), flagged.getStatus());
        verify(ledgerRepository, never()).findByIdForUpdate(anyString());
        verify(ledgerEventRepository, never()).save(any());
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "expected " + expected + " but was " + actual);
    }
}