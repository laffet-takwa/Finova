package com.finova.transaction.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finova.common.event.DomainEvent;
import com.finova.common.event.EventType;
import com.finova.common.web.CorrelationId;
import com.finova.transaction.domain.OutboxEvent;
import com.finova.transaction.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Transactional outbox writer.
 * <p>
 * Publishing straight from a {@code @Transactional} method would either lose
 * events when the broker is down or announce events for work that later rolls
 * back. Instead the fully serialised envelope is inserted into
 * {@code outbox_event} inside the same transaction as the state change, and a
 * scheduled drainer forwards it. Consumers therefore observe either both the
 * state change and the event, or neither.
 */
@Component
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);
    private static final String SERVICE = "transaction-service";

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String topic, String eventKey, EventType eventType, Object payload) {
        String correlationId = org.slf4j.MDC.get(CorrelationId.MDC_KEY);
        DomainEvent<?> envelope = DomainEvent.of(eventType, topic, SERVICE, correlationId, payload);
        OutboxEvent event = new OutboxEvent();
        event.setId(UUID.randomUUID().toString());
        event.setTopic(topic);
        event.setEventKey(eventKey == null ? envelope.eventId() : eventKey);
        event.setPayload(serialise(envelope));
        repository.save(event);
        log.debug("Outbox event queued topic={} key={} eventId={}", topic, event.getEventKey(), envelope.eventId());
    }

    private String serialise(DomainEvent<?> envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Outbox payload could not be serialised for topic " + envelope.topic(), ex);
        }
    }
}