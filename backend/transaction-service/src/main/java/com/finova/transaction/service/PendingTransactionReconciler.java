package com.finova.transaction.service;

import com.finova.common.audit.AuditAction;
import com.finova.common.domain.TransactionStatus;
import com.finova.common.event.EventType;
import com.finova.common.event.Topics;
import com.finova.transaction.config.TransactionProperties;
import com.finova.transaction.domain.Transaction;
import com.finova.transaction.event.OutboxService;
import com.finova.transaction.event.TransactionEventFactory;
import com.finova.transaction.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Re-drives transfers that never received a fraud decision.
 * <p>
 * A transfer left {@code PENDING} beyond the threshold is republished on
 * {@code transaction.created}. Republishing is safe because the fraud service
 * dedupes on {@code transactionId}, so the flow converges instead of creating a
 * second alert, and because a transaction that was settled in the meantime is
 * already terminal and simply ignored downstream.
 */
@Component
@ConditionalOnProperty(name = "finova.transactions.reconciler.enabled", havingValue = "true",
        matchIfMissing = true)
public class PendingTransactionReconciler {

    private static final Logger log = LoggerFactory.getLogger(PendingTransactionReconciler.class);
    private static final int BATCH_SIZE = 100;

    private final TransactionRepository repository;
    private final OutboxService outbox;
    private final TransactionEventFactory events;
    private final TransactionProperties properties;
    private final Clock clock;

    public PendingTransactionReconciler(TransactionRepository repository,
                                        OutboxService outbox,
                                        TransactionEventFactory events,
                                        TransactionProperties properties,
                                        Clock clock) {
        this.repository = repository;
        this.outbox = outbox;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${finova.transactions.reconcile-interval-ms:15000}")
    @Transactional
    public void republishStaleTransactions() {
        Instant threshold = staleBefore(Instant.now(clock));
        List<Transaction> stale = stalePending(threshold, BATCH_SIZE);
        if (stale.isEmpty()) {
            return;
        }
        log.info("Reconciler re-driving {} stale PENDING transfer(s) created before {}",
                stale.size(), threshold);
        for (Transaction transaction : stale) {
            republish(transaction);
        }
    }

    /**
     * Republishing is wrapped in a transaction by the scheduled caller; the
     * outbox enqueue is {@code Propagation.MANDATORY}, so it can only ever join a
     * real transaction and never silently write outside one.
     */
    void republish(Transaction transaction) {
        outbox.enqueue(Topics.TRANSACTION_CREATED, transaction.getId(), EventType.TRANSACTION_CREATED,
                events.from(transaction));
        outbox.enqueue(Topics.AUDIT_RECORDED, transaction.getId(), EventType.AUDIT_RECORDED,
                events.audit(transaction, AuditAction.TRANSFER_CREATED.name(),
                        com.finova.common.event.AuditRecordEvent.RESULT_SUCCESS,
                        transaction.getRequestedByUserId(), transaction.getCorrelationId()));
    }

    Instant staleBefore(Instant now) {
        return now.minus(Duration.ofSeconds(properties.getReconcileThresholdSeconds()));
    }

    List<Transaction> stalePending(Instant threshold, int limit) {
        return repository.findByStatus(TransactionStatus.PENDING, PageRequest.of(0, limit))
                .stream()
                .filter(transaction -> transaction.getCreatedAt() != null
                        && transaction.getCreatedAt().isBefore(threshold))
                .toList();
    }
}