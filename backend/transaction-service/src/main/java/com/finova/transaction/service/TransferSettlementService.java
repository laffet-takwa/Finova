package com.finova.transaction.service;

import com.finova.common.audit.AuditAction;
import com.finova.common.domain.AccountStatus;
import com.finova.common.domain.TransactionStatus;
import com.finova.common.error.BusinessException;
import com.finova.common.error.ErrorCode;
import com.finova.common.event.AuditRecordEvent;
import com.finova.common.event.EventType;
import com.finova.common.event.Topics;
import com.finova.common.support.Money;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Moves money, exactly once, under a pessimistic lock.
 * <p>
 * This is the authoritative settlement step. It is driven by the fraud service
 * decision on {@code transaction.approved}, it re-verifies every precondition
 * inside the locked unit of work because the pre-check performed at creation
 * time is only advisory, and it writes double-entry ledger rows so the money
 * history can be replayed independently of the balance column.
 */
@Service
public class TransferSettlementService {

    private static final Logger log = LoggerFactory.getLogger(TransferSettlementService.class);

    private final TransactionRepository transactionRepository;
    private final LedgerAccountRepository ledgerRepository;
    private final LedgerEventRepository ledgerEventRepository;
    private final TransactionEventMarkerRepository markerRepository;
    private final TransactionEventFactory events;
    private final OutboxService outbox;
    private final Clock clock;

    public TransferSettlementService(TransactionRepository transactionRepository,
                                     LedgerAccountRepository ledgerRepository,
                                     LedgerEventRepository ledgerEventRepository,
                                     TransactionEventMarkerRepository markerRepository,
                                     TransactionEventFactory events,
                                     OutboxService outbox,
                                     Clock clock) {
        this.transactionRepository = transactionRepository;
        this.ledgerRepository = ledgerRepository;
        this.ledgerEventRepository = ledgerEventRepository;
        this.markerRepository = markerRepository;
        this.events = events;
        this.outbox = outbox;
        this.clock = clock;
    }

    /**
     * Settles a previously approved transfer.
     *
     * @param sourceTopic topic the decision arrived on, part of the marker key
     * @param eventId     envelope event id, the consumer-side dedupe key
     */
    @Transactional
    public void settle(String transactionId, String sourceTopic, String eventId) {
        if (markerRepository.existsByTopicAndEventId(sourceTopic, eventId)) {
            log.debug("Settlement ignored, decision already processed topic={} eventId={}", sourceTopic, eventId);
            return;
        }
        Transaction transaction = transactionRepository.findByIdForUpdate(transactionId)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.TRANSACTION_NOT_FOUND,
                        "Transaction", transactionId));
        if (!TransactionStatus.PENDING.name().equals(transaction.getStatus())) {
            recordMarker(sourceTopic, eventId, transaction.getId());
            log.debug("Settlement ignored, transaction already decided id={} status={}",
                    transactionId, transaction.getStatus());
            return;
        }

        Map<String, LedgerAccount> locked = lockPairInOrder(
                transaction.getSenderAccountId(), transaction.getReceiverAccountId());
        LedgerAccount sender = locked.get(transaction.getSenderAccountId());
        LedgerAccount receiver = locked.get(transaction.getReceiverAccountId());
        if (sender == null || receiver == null) {
            fail(transaction, sourceTopic, eventId, "A ledger account required by this transfer no longer exists.");
            return;
        }
        if (!isActive(sender) || !isActive(receiver)) {
            fail(transaction, sourceTopic, eventId, "An account involved in this transfer is not active.");
            return;
        }
        BigDecimal amount = requireAmount(transaction);
        if (sender.getBalance().compareTo(amount) < 0) {
            fail(transaction, sourceTopic, eventId, ErrorCode.INSUFFICIENT_BALANCE.defaultMessage());
            return;
        }

        Movement debit = debit(sender, amount);
        Movement credit = credit(receiver, amount);
        ledgerEventRepository.save(leg(transaction, sender, LedgerEvent.DIRECTION_DEBIT, amount, debit));
        ledgerEventRepository.save(leg(transaction, receiver, LedgerEvent.DIRECTION_CREDIT, amount, credit));

        transaction.setStatus(TransactionStatus.COMPLETED.name());
        transaction.setCompletedAt(Instant.now(clock));
        transaction.setFailureReason(null);
        transaction.setSettledSenderBalance(sender.getBalance());
        transaction.setSettledReceiverBalance(receiver.getBalance());
        transactionRepository.save(transaction);
        recordMarker(sourceTopic, eventId, transaction.getId());

        outbox.enqueue(Topics.TRANSACTION_COMPLETED, transaction.getId(), EventType.TRANSACTION_COMPLETED,
                events.from(transaction));
        outbox.enqueue(Topics.AUDIT_RECORDED, transaction.getId(), EventType.AUDIT_RECORDED,
                events.audit(transaction, AuditAction.TRANSFER_COMPLETED.name(), AuditRecordEvent.RESULT_SUCCESS,
                        transaction.getRequestedByUserId(), transaction.getCorrelationId()));
        log.info("Transfer settled reference={} amount={} {} senderBalance={} receiverBalance={}",
                transaction.getReference(), amount, transaction.getCurrency(),
                sender.getBalance(), receiver.getBalance());
    }

    /**
     * Holds a high-risk transfer for manual review. No money moves and no ledger
     * row is written; the risk detail is stored so the admin screens and the audit
     * trail show exactly why the funds are frozen.
     */
    @Transactional
    public void holdForReview(SettlementDecision decision, String sourceTopic, String eventId) {
        if (markerRepository.existsByTopicAndEventId(sourceTopic, eventId)) {
            log.debug("Hold ignored, decision already processed topic={} eventId={}", sourceTopic, eventId);
            return;
        }
        Transaction transaction = transactionRepository.findByIdForUpdate(decision.transactionId())
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.TRANSACTION_NOT_FOUND,
                        "Transaction", decision.transactionId()));
        if (!TransactionStatus.PENDING.name().equals(transaction.getStatus())) {
            recordMarker(sourceTopic, eventId, transaction.getId());
            return;
        }
        transaction.setStatus(TransactionStatus.FLAGGED.name());
        transaction.setRiskScore(decision.riskScore());
        transaction.setRiskLevel(decision.riskLevel());
        transaction.setRiskReasons(decision.reasons());
        transaction.setFailureReason("Held for fraud review: " + summarise(decision.reasons()));
        transactionRepository.save(transaction);
        recordMarker(sourceTopic, eventId, transaction.getId());

        outbox.enqueue(Topics.AUDIT_RECORDED, transaction.getId(), EventType.AUDIT_RECORDED,
                events.audit(transaction, AuditAction.FRAUD_DETECTED.name(), AuditRecordEvent.RESULT_FAILURE,
                        transaction.getRequestedByUserId(), transaction.getCorrelationId()));
        log.warn("Transfer held for review reference={} riskScore={} riskLevel={}",
                transaction.getReference(), decision.riskScore(), decision.riskLevel());
    }

    /**
     * Locks both ledger rows in ascending primary-key order, one statement at a
     * time.
     * <p>
     * The ids are read without a lock first, sorted, and only then taken with
     * {@code PESSIMISTIC_WRITE}. Every settlement in the platform therefore
     * acquires a pair in the same global order, so two transfers moving money in
     * opposite directions can never each hold the first row while waiting for the
     * second: the second transaction simply blocks until the first commits, and no
     * wait-for cycle can form.
     */
    private Map<String, LedgerAccount> lockPairInOrder(String accountIdA, String accountIdB) {
        List<String> ids = ledgerRepository.findIdsByAccountIdIn(List.of(accountIdA, accountIdB));
        if (ids.size() != 2) {
            return Map.of();
        }
        List<String> ordered = ids.stream().sorted().toList();
        Map<String, LedgerAccount> locked = new HashMap<>();
        for (String ledgerId : ordered) {
            LedgerAccount account = ledgerRepository.findByIdForUpdate(ledgerId)
                    .orElseThrow(() -> BusinessException.notFound(ErrorCode.ACCOUNT_NOT_FOUND,
                            "Ledger account", ledgerId));
            locked.put(account.getAccountId(), account);
        }
        return locked;
    }

    private Movement debit(LedgerAccount sender, BigDecimal amount) {
        BigDecimal before = Money.scale(sender.getBalance());
        BigDecimal after = Money.scale(before.subtract(amount));
        sender.setBalance(after);
        return new Movement(before, after);
    }

    private Movement credit(LedgerAccount receiver, BigDecimal amount) {
        BigDecimal before = Money.scale(receiver.getBalance());
        BigDecimal after = Money.scale(before.add(amount));
        receiver.setBalance(after);
        return new Movement(before, after);
    }

    private LedgerEvent leg(Transaction transaction, LedgerAccount account, String direction, BigDecimal amount,
                            Movement movement) {
        LedgerEvent event = new LedgerEvent();
        event.setId(UUID.randomUUID().toString());
        event.setTransactionId(transaction.getId());
        event.setLedgerAccountId(account.getId());
        event.setAccountId(account.getAccountId());
        event.setDirection(direction);
        event.setAmount(amount);
        event.setBalanceBefore(movement.before());
        event.setBalanceAfter(movement.after());
        event.setCurrency(transaction.getCurrency());
        event.setCreatedAt(Instant.now(clock));
        event.setReference(transaction.getReference());
        return event;
    }

    private void recordMarker(String sourceTopic, String eventId, String transactionId) {
        TransactionEventMarker marker = new TransactionEventMarker();
        marker.setId(UUID.randomUUID().toString());
        marker.setTopic(sourceTopic);
        marker.setEventId(eventId);
        marker.setTransactionId(transactionId);
        marker.setProcessedAt(Instant.now(clock));
        markerRepository.save(marker);
    }

    /**
     * Terminal failure. The status is written immediately - a transfer is never
     * left in a non-terminal state - and {@code transaction.failed} plus the audit
     * record are queued on the outbox inside the same transaction.
     */
    private void fail(Transaction transaction, String sourceTopic, String eventId, String reason) {
        transaction.setStatus(TransactionStatus.FAILED.name());
        transaction.setFailureReason(reason);
        transaction.setCompletedAt(Instant.now(clock));
        transactionRepository.save(transaction);
        recordMarker(sourceTopic, eventId, transaction.getId());

        outbox.enqueue(Topics.TRANSACTION_FAILED, transaction.getId(), EventType.TRANSACTION_FAILED,
                events.from(transaction));
        outbox.enqueue(Topics.AUDIT_RECORDED, transaction.getId(), EventType.AUDIT_RECORDED,
                events.audit(transaction, AuditAction.TRANSFER_FAILED.name(), AuditRecordEvent.RESULT_FAILURE,
                        transaction.getRequestedByUserId(), transaction.getCorrelationId()));
        log.warn("Transfer failed reference={} reason={}", transaction.getReference(), reason);
    }

    private boolean isActive(LedgerAccount account) {
        return AccountStatus.ACTIVE.name().equals(account.getStatus());
    }

    private BigDecimal requireAmount(Transaction transaction) {
        if (transaction.getAmount() == null || transaction.getAmount().signum() <= 0) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "Transaction " + transaction.getReference() + " carries a non positive amount.");
        }
        return Money.scale(transaction.getAmount());
    }

    private String summarise(List<String> reasons) {
        if (reasons == null || reasons.isEmpty()) {
            return "no reason supplied by the fraud engine";
        }
        return String.join("; ", reasons.stream().limit(3).toList());
    }

    /** Balance pair produced by one leg of the movement. */
    private record Movement(BigDecimal before, BigDecimal after) {
    }
}