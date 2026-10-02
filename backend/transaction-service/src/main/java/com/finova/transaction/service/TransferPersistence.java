package com.finova.transaction.service;

import com.finova.common.audit.AuditAction;
import com.finova.common.domain.TransactionStatus;
import com.finova.common.event.AuditRecordEvent;
import com.finova.common.event.EventType;
import com.finova.common.event.Topics;
import com.finova.common.web.CorrelationId;
import com.finova.transaction.domain.LedgerAccount;
import com.finova.transaction.domain.Transaction;
import com.finova.transaction.dto.TransferRequest;
import com.finova.transaction.event.OutboxService;
import com.finova.transaction.event.TransactionEventFactory;
import com.finova.transaction.repository.TransactionRepository;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * The single transactional unit that turns an accepted transfer into a
 * persisted {@code PENDING} row plus the outbox events that announce it.
 * <p>
 * Reference generation, the insert and the two outbox writes share one
 * transaction, so a transfer can never exist without the events that drive it
 * forward, and a failed insert leaves no half-announced transfer behind.
 */
@Service
public class TransferPersistence {

    private final TransactionRepository transactionRepository;
    private final ReferenceGenerator referenceGenerator;
    private final TransactionEventFactory events;
    private final OutboxService outbox;
    private final Clock clock;

    public TransferPersistence(TransactionRepository transactionRepository,
                               ReferenceGenerator referenceGenerator,
                               TransactionEventFactory events,
                               OutboxService outbox,
                               Clock clock) {
        this.transactionRepository = transactionRepository;
        this.referenceGenerator = referenceGenerator;
        this.events = events;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public Transaction insert(TransferRequest request, String idempotencyKey, String requestFingerprint,
                              LedgerAccount sender, LedgerAccount receiver, TransferValidation validation,
                              String requestedByUserId, String ipAddress) {
        Transaction transaction = build(request, idempotencyKey, requestFingerprint, sender, receiver, validation,
                requestedByUserId, ipAddress, referenceGenerator.next());
        Transaction saved = transactionRepository.save(transaction);
        outbox.enqueue(Topics.TRANSACTION_CREATED, saved.getId(), EventType.TRANSACTION_CREATED, events.from(saved));
        outbox.enqueue(Topics.AUDIT_RECORDED, saved.getId(), EventType.AUDIT_RECORDED,
                events.audit(saved, AuditAction.TRANSFER_CREATED.name(), AuditRecordEvent.RESULT_SUCCESS,
                        requestedByUserId, saved.getCorrelationId()));
        return saved;
    }

    Transaction build(TransferRequest request, String idempotencyKey, String requestFingerprint,
                      LedgerAccount sender, LedgerAccount receiver, TransferValidation validation,
                      String requestedByUserId, String ipAddress, String reference) {
        Transaction transaction = new Transaction();
        transaction.setId(UUID.randomUUID().toString());
        transaction.setReference(reference);
        transaction.setIdempotencyKey(idempotencyKey);
        transaction.setRequestFingerprint(requestFingerprint);
        transaction.setSenderAccountId(sender.getAccountId());
        transaction.setReceiverAccountId(receiver.getAccountId());
        transaction.setSenderAccountNumber(sender.getAccountNumber());
        transaction.setReceiverAccountNumber(receiver.getAccountNumber());
        transaction.setSenderUserId(sender.getUserId());
        transaction.setReceiverUserId(receiver.getUserId());
        transaction.setAmount(validation.amount());
        transaction.setCurrency(validation.currency());
        transaction.setFee(validation.fee());
        transaction.setDescription(request.description());
        transaction.setType(validation.type().name());
        transaction.setStatus(TransactionStatus.PENDING.name());
        transaction.setCreatedAt(Instant.now(clock));
        transaction.setCorrelationId(currentCorrelationId());
        transaction.setRequestedByUserId(requestedByUserId);
        transaction.setIpAddress(ipAddress);
        return transaction;
    }

    private String currentCorrelationId() {
        String correlationId = MDC.get(CorrelationId.MDC_KEY);
        return correlationId == null ? "unknown" : correlationId;
    }
}