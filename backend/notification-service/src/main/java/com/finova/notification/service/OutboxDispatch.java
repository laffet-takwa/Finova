package com.finova.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finova.common.event.DomainEvent;
import com.finova.common.event.NotificationCreatedEvent;
import com.finova.notification.domain.OutboxEvent;
import com.finova.notification.event.NotificationEventPublisher;
import com.finova.notification.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publishes a single pending outbox row.
 * <p>
 * Each row gets its own short transaction: the send is awaited inside it and the
 * outcome (published timestamp, or the attempt counter together with the last
 * error) is committed either way. A broker outage therefore costs one row per tick
 * instead of stalling the whole drain, and the row stays pending until it is
 * genuinely on the wire.
 */
@Component
public class OutboxDispatch {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatch.class);
    private static final int MAX_ERROR_LENGTH = 500;

    private final OutboxEventRepository outboxEventRepository;
    private final NotificationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final long sendTimeoutMillis;

    public OutboxDispatch(OutboxEventRepository outboxEventRepository,
                          NotificationEventPublisher eventPublisher,
                          ObjectMapper objectMapper,
                          @Value("${finova.notifications.outbox-send-timeout-ms:3000}") long sendTimeoutMillis) {
        this.outboxEventRepository = outboxEventRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.sendTimeoutMillis = sendTimeoutMillis;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean deliver(String outboxId) {
        OutboxEvent row = outboxEventRepository.findById(outboxId).orElse(null);
        if (row == null || row.getPublishedAt() != null) {
            return false;
        }
        row.setAttempts(row.getAttempts() + 1);
        try {
            eventPublisher.publish(row.getTopic(), row.getEventKey(), readEnvelope(row.getPayload()))
                    .get(sendTimeoutMillis, TimeUnit.MILLISECONDS);
            row.setPublishedAt(Instant.now());
            row.setLastError(null);
            outboxEventRepository.save(row);
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            recordFailure(row, ex);
            return false;
        } catch (ExecutionException | TimeoutException | JsonProcessingException | RuntimeException ex) {
            recordFailure(row, ex);
            return false;
        }
    }

    private DomainEvent<NotificationCreatedEvent> readEnvelope(String payload)
            throws JsonProcessingException {
        return objectMapper.readValue(payload, new TypeReference<DomainEvent<NotificationCreatedEvent>>() {
        });
    }

    private void recordFailure(OutboxEvent row, Exception ex) {
        row.setLastError(truncate(ex.getClass().getSimpleName() + ": " + ex.getMessage()));
        outboxEventRepository.save(row);
        log.warn("Outbox publish failed topic={} dedupeKey={} attempts={}",
                row.getTopic(), row.getDedupeKey(), row.getAttempts(), ex);
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }
}