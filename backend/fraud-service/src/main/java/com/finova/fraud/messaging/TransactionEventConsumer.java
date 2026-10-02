package com.finova.fraud.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finova.common.domain.FraudStatus;
import com.finova.common.event.DomainEvent;
import com.finova.common.event.Topics;
import com.finova.common.event.TransactionEvent;
import com.finova.common.web.CorrelationId;
import com.finova.fraud.domain.FraudAlert;
import com.finova.fraud.domain.FraudTimelineKey;
import com.finova.fraud.domain.TimelineStep;
import com.finova.fraud.repository.FraudAlertRepository;
import com.finova.fraud.service.FraudScoringService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * Consumes the transaction topics the fraud service cares about.
 * <p>
 * Handlers never rethrow: a poison message must not kill the consumer loop, and a failed evaluation
 * can safely be retried by the transaction-service reconciler republishing {@code
 * transaction.created}.
 */
@Component
public class TransactionEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventConsumer.class);
    private static final String GROUP_ID = "fraud-service";

    private final ObjectMapper objectMapper;
    private final FraudScoringService scoringService;
    private final FraudAlertRepository alertRepository;

    public TransactionEventConsumer(ObjectMapper objectMapper,
                                    FraudScoringService scoringService,
                                    FraudAlertRepository alertRepository) {
        this.objectMapper = objectMapper;
        this.scoringService = scoringService;
        this.alertRepository = alertRepository;
    }

    @KafkaListener(topics = Topics.TRANSACTION_CREATED, groupId = GROUP_ID)
    public void onTransactionCreated(ConsumerRecord<String, DomainEvent> record) {
        withCorrelationId(record, () -> {
            DomainEvent<TransactionEvent> event = convert(record);
            log.info("Fraud analysis requested for transaction {} reference {}",
                    event.payload().transactionId(), event.payload().reference());
            scoringService.evaluate(event.payload());
        });
    }

    @KafkaListener(topics = Topics.TRANSACTION_COMPLETED, groupId = GROUP_ID)
    public void onTransactionCompleted(ConsumerRecord<String, DomainEvent> record) {
        withCorrelationId(record, () -> {
            DomainEvent<TransactionEvent> event = convert(record);
            resolve(event, "COMPLETED", FraudTimelineKey.TRANSACTION_SETTLED,
                    "The transaction service settled the transfer; the alert is closed as safe.",
                    true);
        });
    }

    @KafkaListener(topics = Topics.TRANSACTION_FAILED, groupId = GROUP_ID)
    public void onTransactionFailed(ConsumerRecord<String, DomainEvent> record) {
        withCorrelationId(record, () -> {
            DomainEvent<TransactionEvent> event = convert(record);
            resolve(event, "FAILED", FraudTimelineKey.TRANSACTION_FAILED,
                    "The transaction service rejected the transfer before settlement.",
                    false);
        });
    }

    /**
     * Records the terminal outcome of a transfer on its alert.
     * <p>
     * A settlement means the money already moved, so an alert still waiting on a reviewer is closed.
     * A failure is recorded but never closes the alert: a rejected transfer is not evidence of fraud.
     */
    private void resolve(DomainEvent<TransactionEvent> event, String outcome,
                         FraudTimelineKey stepKey, String description, boolean closeWhenOpen) {
        Optional<FraudAlert> found = alertRepository.findByTransactionId(event.payload().transactionId());
        if (found.isEmpty()) {
            log.debug("No fraud alert for transaction {}, nothing to resolve", event.payload().transactionId());
            return;
        }
        FraudAlert alert = found.get();
        Instant now = Instant.now();
        alert.setResolvedByTransaction(outcome);
        alert.setUpdatedAt(now);
        alert.appendTimeline(TimelineStep.at(stepKey.name(), stepKey.label(), description, now));
        if (closeWhenOpen && isUnresolved(alert)) {
            alert.setStatus(FraudStatus.SAFE.name());
            alert.setReviewedAt(now);
        }
        alertRepository.save(alert);
        log.info("Recorded transaction {} outcome on fraud alert {}", outcome, alert.getId());
    }

    private boolean isUnresolved(FraudAlert alert) {
        return FraudStatus.OPEN.name().equals(alert.getStatus())
                || FraudStatus.UNDER_REVIEW.name().equals(alert.getStatus());
    }

    private DomainEvent<TransactionEvent> convert(ConsumerRecord<String, DomainEvent> record) {
        return objectMapper.convertValue(record.value(), new TypeReference<DomainEvent<TransactionEvent>>() {
        });
    }

    private void withCorrelationId(ConsumerRecord<String, DomainEvent> record, Runnable handler) {
        String correlationId = Optional.ofNullable(record.headers().lastHeader(CorrelationId.HEADER))
                .map(header -> new String(header.value(), java.nio.charset.StandardCharsets.UTF_8))
                .orElse(null);
        try {
            if (correlationId != null && !correlationId.isBlank()) {
                MDC.put(CorrelationId.MDC_KEY, correlationId);
            }
            handler.run();
        } catch (Exception failure) {
            log.error("Fraud consumer failed on topic {} partition {} offset {}: {}",
                    record.topic(), record.partition(), record.offset(), failure.getMessage(), failure);
        } finally {
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }
}
