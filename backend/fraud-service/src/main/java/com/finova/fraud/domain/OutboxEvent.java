package com.finova.fraud.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Transactional outbox row.
 * <p>
 * The decision event is written here together with the alert that justified it and is drained by a
 * scheduled publisher, so a crash between the Mongo write and the Kafka send can never lose a
 * decision. {@code dedupeKey} is unique, which turns the at-least-once drain into an effectively
 * exactly-once publish per (aggregate, topic) pair.
 */
@Document(collection = "outbox_event")
@CompoundIndexes({
        @CompoundIndex(name = "idx_outbox_pending", def = "{'publishedAt':1,'createdAt':1}"),
        @CompoundIndex(name = "uq_outbox_dedupe", def = "{'dedupeKey':1}", unique = true)
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {

    @Id
    private String id;

    private String dedupeKey;
    private String aggregateId;
    private String topic;
    /**
     * Logical event name. Held as a string rather than the {@code EventType} enum because
     * {@code TRANSACTION_APPROVED} does not exist in finova-common yet.
     */
    private String eventType;
    private String messageKey;
    private String payload;
    private String correlationId;

    @Indexed
    private Instant createdAt;

    private Instant publishedAt;
    private int attempts;
    private String lastError;

    public boolean isPending() {
        return publishedAt == null;
    }
}
