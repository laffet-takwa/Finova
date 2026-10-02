package com.finova.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Envelope wrapped around every Kafka message published by Finova.
 * <p>
 * {@code eventId} gives consumers idempotency, {@code correlationId} ties the
 * message back to the originating HTTP request so a transfer can be followed
 * from the gateway all the way to the notification inbox.
 */
public record DomainEvent<T>(
        String eventId,
        EventType eventType,
        String topic,
        Instant timestamp,
        String correlationId,
        String sourceService,
        T payload
) {
    public static <T> DomainEvent<T> of(EventType type, String topic, String sourceService,
                                       String correlationId, T payload) {
        return new DomainEvent<>(UUID.randomUUID().toString(), type, topic, Instant.now(),
                correlationId == null ? "unknown" : correlationId, sourceService, payload);
    }
}
