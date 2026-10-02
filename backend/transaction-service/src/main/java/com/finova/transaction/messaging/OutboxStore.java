package com.finova.transaction.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finova.common.event.DomainEvent;
import com.finova.transaction.domain.OutboxEvent;
import com.finova.transaction.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Outbox persistence helpers.
 * <p>
 * Each state change runs in its own {@code REQUIRES_NEW} transaction so a failed
 * send can be recorded without being undone by the rollback of the surrounding
 * work, and so two drainer threads never share a persistence context.
 */
@Service
public class OutboxStore {

    private static final Logger log = LoggerFactory.getLogger(OutboxStore.class);

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxStore(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<OutboxEvent> findUnpublished(int batchSize) {
        return repository.findUnpublished(PageRequest.of(0, batchSize));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(String id) {
        repository.findById(id).ifPresent(event -> {
            event.setPublishedAt(Instant.now());
            repository.save(event);
        });
    }

    /**
     * Records a failed attempt. After the configured ceiling the row is stamped
     * as published so a permanently poisoned payload cannot block the queue; the
     * discard is logged loudly because it means a lost event.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String id, int maxAttempts) {
        repository.findById(id).ifPresent(event -> {
            int attempts = event.getAttempts() + 1;
            event.setAttempts(attempts);
            if (attempts >= maxAttempts) {
                log.error("Outbox event discarded after {} attempts id={} topic={} eventKey={}",
                        attempts, event.getId(), event.getTopic(), event.getEventKey());
                event.setPublishedAt(Instant.now());
            }
            repository.save(event);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public DomainEvent<?> readEnvelope(OutboxEvent event) {
        try {
            return objectMapper.readValue(event.getPayload(), DomainEvent.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Outbox payload for id " + event.getId() + " is unreadable", ex);
        }
    }
}