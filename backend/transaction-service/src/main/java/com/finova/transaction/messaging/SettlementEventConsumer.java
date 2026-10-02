package com.finova.transaction.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finova.common.error.BusinessException;
import com.finova.common.event.DomainEvent;
import com.finova.common.event.Topics;
import com.finova.common.event.TransactionEvent;
import com.finova.common.web.CorrelationId;
import com.finova.transaction.service.SettlementDecision;
import com.finova.transaction.service.TransferSettlementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes the fraud service settlement decisions.
 * <p>
 * {@code transaction.approved} is the decision to proceed: LOW or MEDIUM risk, or
 * an administrator releasing an alert that had been held. It drives settlement.
 * {@code transaction.flagged} is the decision to hold: HIGH risk, and no money
 * moves until the alert is released and {@code transaction.approved} is
 * republished.
 * <p>
 * There is deliberately no listener on {@code transaction.completed}: that topic
 * is this service's own settlement fact, and consuming it would re-settle the
 * transfer it just settled. The consumer also never rethrows - an unrecoverable
 * error would stop the partition and strand every later transfer behind it.
 */
@Component
public class SettlementEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(SettlementEventConsumer.class);
    private static final TypeReference<DomainEvent<TransactionEvent>> PAYLOAD_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final TransferSettlementService settlementService;

    public SettlementEventConsumer(ObjectMapper objectMapper, TransferSettlementService settlementService) {
        this.objectMapper = objectMapper;
        this.settlementService = settlementService;
    }

    @KafkaListener(topics = Topics.TRANSACTION_APPROVED, groupId = "transaction-service-settlement")
    public void onApproved(String raw) {
        handle(raw, (envelope, payload) -> settlementService.settle(
                payload.transactionId(), Topics.TRANSACTION_APPROVED, envelope.eventId()));
    }

    @KafkaListener(topics = Topics.TRANSACTION_FLAGGED, groupId = "transaction-service-settlement")
    public void onFlagged(String raw) {
        handle(raw, (envelope, payload) -> settlementService.holdForReview(
                new SettlementDecision(payload.transactionId(), payload.riskScore(), payload.riskLevel(),
                        payload.safeReasons()),
                Topics.TRANSACTION_FLAGGED, envelope.eventId()));
    }

    private void handle(String raw, DecisionHandler decisionHandler) {
        try {
            DomainEvent<TransactionEvent> envelope = objectMapper.readValue(raw, PAYLOAD_TYPE);
            MDC.put(CorrelationId.MDC_KEY, envelope.correlationId());
            if (envelope.payload() == null || envelope.payload().transactionId() == null) {
                log.warn("Ignoring decision without a transaction payload eventId={}", envelope.eventId());
                return;
            }
            decisionHandler.handle(envelope, envelope.payload());
        } catch (BusinessException ex) {
            log.warn("Settlement decision rejected code={} reason={}", ex.getErrorCode(), ex.getMessage());
        } catch (Exception ex) {
            log.error("Settlement decision could not be handled: {}", ex.getMessage(), ex);
        } finally {
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }

    @FunctionalInterface
    private interface DecisionHandler {
        void handle(DomainEvent<TransactionEvent> envelope, TransactionEvent payload);
    }
}