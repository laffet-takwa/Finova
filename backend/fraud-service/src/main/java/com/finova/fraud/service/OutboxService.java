package com.finova.fraud.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finova.common.event.DomainEvent;
import com.finova.fraud.config.FraudProperties;
import com.finova.fraud.domain.OutboxEvent;
import com.finova.fraud.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Transactional outbox.
 * <p>
 * The fraud decision is committed to MongoDB before anyone is told about it, so a crash between the
 * alert write and the Kafka send would otherwise strand the transfer forever. Instead the event is
 * serialised into {@code outbox_event} in the same unit of work as the alert, and this scheduler
 * drains the table. Delivery is at-least-once; the unique {@code dedupeKey} keeps the queue itself
 * from ever holding two rows for the same (aggregate, topic), which is what makes the drain
 * effectively exactly-once per decision.
 */
@Service
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, DomainEvent<?>> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final FraudProperties properties;

    public OutboxService(OutboxEventRepository outboxRepository,
                         KafkaTemplate<String, DomainEvent<?>> kafkaTemplate,
                         ObjectMapper objectMapper,
                         FraudProperties properties) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * Queues an event for publication. Re-queuing the same (aggregate, topic) pair is a no-op, so
     * callers can retry freely after a partial failure.
     */
    public void enqueue(String aggregateId, String topic, String eventTypeName, String messageKey,
                        DomainEvent<?> event) {
        String dedupeKey = aggregateId + ":" + topic;
        if (outboxRepository.existsByDedupeKey(dedupeKey)) {
            log.debug("Outbox already holds {} for {}", topic, aggregateId);
            return;
        }
        OutboxEvent row = OutboxEvent.builder()
                .id(UUID.randomUUID().toString())
                .dedupeKey(dedupeKey)
                .aggregateId(aggregateId)
                .topic(topic)
                .eventType(eventTypeName)
                .messageKey(messageKey)
                .payload(writePayload(event))
                .correlationId(event == null ? null : event.correlationId())
                .createdAt(Instant.now())
                .attempts(0)
                .build();
        try {
            outboxRepository.insert(row);
        } catch (DuplicateKeyException raced) {
            log.debug("Outbox insert for {} on {} lost the race, another worker queued it", topic,
                    aggregateId);
        }
    }

    @Scheduled(fixedDelayString = "${finova.fraud.outbox.publish-interval-ms:2000}")
    public void drain() {
        List<OutboxEvent> pending = outboxRepository
                .findByPublishedAtIsNullOrderByCreatedAtAsc(PageRequest.of(0, properties.getOutbox().getBatchSize()));
        if (pending.isEmpty()) {
            return;
        }
        for (OutboxEvent row : pending) {
            publish(row);
        }
        purgePublished();
    }

    private void publish(OutboxEvent row) {
        try {
            DomainEvent<?> event = objectMapper.readValue(row.getPayload(), DomainEvent.class);
            kafkaTemplate.send(row.getTopic(), row.getMessageKey(), event)
                    .get(properties.getOutbox().getSendTimeoutMs(), TimeUnit.MILLISECONDS);
            row.setPublishedAt(Instant.now());
            row.setLastError(null);
            outboxRepository.save(row);
            log.debug("Published outbox {} to {}", row.getId(), row.getTopic());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            recordFailure(row, "interrupted while publishing");
            log.warn("Outbox publish interrupted for {}", row.getId());
        } catch (Exception failure) {
            recordFailure(row, failure.getMessage());
            log.warn("Outbox publish failed for {} on {}: {}", row.getId(), row.getTopic(),
                    failure.getMessage());
        }
    }

    private void recordFailure(OutboxEvent row, String error) {
        row.setAttempts(row.getAttempts() + 1);
        row.setLastError(error == null ? "unknown" : error);
        outboxRepository.save(row);
    }

    private void purgePublished() {
        Instant cutoff = Instant.now().minus(properties.getOutbox().getRetentionDays(), ChronoUnit.DAYS);
        outboxRepository.deleteByPublishedAtBefore(cutoff);
    }

    private String writePayload(DomainEvent<?> event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to serialise the outbox payload", failure);
        }
    }
}
