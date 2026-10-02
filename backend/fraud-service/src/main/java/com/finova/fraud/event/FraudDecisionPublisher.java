package com.finova.fraud.event;

import com.finova.common.audit.AuditAction;
import com.finova.common.domain.RiskLevel;
import com.finova.common.event.AccountBlockedEvent;
import com.finova.common.event.AuditRecordEvent;
import com.finova.common.event.DomainEvent;
import com.finova.common.event.EventType;
import com.finova.common.event.Topics;
import com.finova.common.event.TransactionEvent;
import com.finova.common.web.CorrelationId;
import com.finova.fraud.domain.FraudAlert;
import com.finova.fraud.service.OutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a fraud decision or an administrator action into outbox rows.
 * <p>
 * Nothing here calls Kafka directly: every message is queued and published by {@link OutboxService},
 * so a decision is never announced before it is durable.
 */
@Service
public class FraudDecisionPublisher {

    private static final Logger log = LoggerFactory.getLogger(FraudDecisionPublisher.class);
    private static final String SERVICE = "fraud-service";

    /**
     * Provisional envelope type for {@code transaction.approved}. {@code EventType} in finova-common
     * has no {@code TRANSACTION_APPROVED} constant yet, so the closest existing constant carries the
     * envelope while the topic and the outbox row both carry the true logical name. Replace with
     * {@code EventType.TRANSACTION_APPROVED} once the constant lands.
     */
    private static final EventType PROVISIONAL_APPROVED_TYPE = EventType.TRANSACTION_COMPLETED;

    private final OutboxService outboxService;

    public FraudDecisionPublisher(OutboxService outboxService) {
        this.outboxService = outboxService;
    }

    /**
     * Publishes the analysis outcome for a transaction.
     * <p>
     * {@code HIGH} holds the funds on {@code transaction.flagged}. {@code LOW} and {@code MEDIUM}
     * release them on {@code transaction.approved} with the status left as the transaction service
     * sent it ({@code PENDING}), so settlement still belongs to the transaction service and the score
     * still lands on the transaction row for the admin console.
     */
    public void publishDecision(FraudAlert alert, TransactionEvent source) {
        boolean highRisk = RiskLevel.HIGH.name().equals(alert.getRiskLevel());
        String topic = highRisk ? Topics.TRANSACTION_FLAGGED : FraudTopics.TRANSACTION_APPROVED;
        String eventTypeName = highRisk ? EventType.TRANSACTION_FLAGGED.name()
                : FraudTopics.TRANSACTION_APPROVED_TYPE;
        String status = highRisk ? "FLAGGED" : source.status();

        TransactionEvent decision = copyOf(source, status, alert, null);
        DomainEvent<TransactionEvent> event = DomainEvent.of(
                highRisk ? EventType.TRANSACTION_FLAGGED : PROVISIONAL_APPROVED_TYPE,
                topic, SERVICE, correlationId(), decision);

        outboxService.enqueue(alert.getTransactionId(), topic, eventTypeName, alert.getTransactionId(), event);
        log.info("Queued fraud decision for transaction {} on {} (score {}, risk {})",
                alert.getTransactionId(), topic, alert.getRiskScore(), alert.getRiskLevel());
    }

    /**
     * Releases the held funds after an administrator marked the alert safe.
     * <p>
     * This is the only path in which the fraud service moves money, and it does not move it: it
     * orders the transaction service to settle the transfer it is still holding.
     */
    public void publishApproval(FraudAlert alert, TransactionEvent source) {
        TransactionEvent decision = copyOf(source, "APPROVED", alert, null);
        DomainEvent<TransactionEvent> event = DomainEvent.of(PROVISIONAL_APPROVED_TYPE,
                FraudTopics.TRANSACTION_APPROVED, SERVICE, correlationId(), decision);
        outboxService.enqueue("release:" + alert.getTransactionId(), FraudTopics.TRANSACTION_APPROVED,
                FraudTopics.TRANSACTION_RELEASED_TYPE, alert.getTransactionId(), event);
        log.info("Queued funds release for transaction {}", alert.getTransactionId());
    }

    public void publishAccountBlocked(AccountBlockedEvent payload) {
        DomainEvent<AccountBlockedEvent> event = DomainEvent.of(EventType.ACCOUNT_BLOCKED,
                Topics.ACCOUNT_BLOCKED, SERVICE, correlationId(), payload);
        outboxService.enqueue("block:" + payload.accountId(), Topics.ACCOUNT_BLOCKED,
                EventType.ACCOUNT_BLOCKED.name(), payload.accountId(), event);
    }

    public void publishAuditRecord(AuditRecordEvent payload) {
        DomainEvent<AuditRecordEvent> event = DomainEvent.of(EventType.AUDIT_RECORDED,
                Topics.AUDIT_RECORDED, SERVICE, correlationId(), payload);
        String action = payload.metadata() == null ? "UNKNOWN" : payload.metadata().getOrDefault("action", "UNKNOWN");
        outboxService.enqueue(payload.resourceId() + ":" + action, Topics.AUDIT_RECORDED,
                EventType.AUDIT_RECORDED.name(), payload.resourceId(), event);
    }

    /** Builds the audit entry every administrator action on an alert must produce. */
    public static AuditRecordEvent auditFor(FraudAlert alert, String action, String note, String actorId,
                                            String correlationId, String message) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("action", action);
        if (note != null) {
            metadata.put("note", note);
        }
        metadata.put("transactionId", alert.getTransactionId());
        return new AuditRecordEvent(
                AuditAction.FRAUD_REVIEWED.name(),
                actorId,
                "fraud_alert",
                alert.getId(),
                null,
                correlationId,
                AuditRecordEvent.RESULT_SUCCESS,
                SERVICE,
                message,
                metadata,
                Instant.now());
    }

    private TransactionEvent copyOf(TransactionEvent source, String status, FraudAlert alert,
                                    String failureReason) {
        return new TransactionEvent(
                source.transactionId(),
                source.reference(),
                source.senderAccountId(),
                source.receiverAccountId(),
                source.senderAccountNumber(),
                source.receiverAccountNumber(),
                source.senderUserId(),
                source.receiverUserId(),
                source.amount(),
                source.currency(),
                source.description(),
                source.type(),
                status,
                alert.getRiskScore(),
                alert.getRiskLevel(),
                alert.getReasons() == null ? List.of() : List.copyOf(alert.getReasons()),
                failureReason,
                source.requestedByUserId(),
                source.ipAddress(),
                source.completedAt());
    }

    private static String correlationId() {
        String value = MDC.get(CorrelationId.MDC_KEY);
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
