package com.finova.transaction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Transactional outbox row.
 * <p>
 * The payload is the serialised {@code DomainEvent} envelope, so the event id,
 * correlation id and payload are frozen at the moment the business transaction
 * committed. A scheduled drainer publishes it to Kafka and stamps
 * {@code publishedAt}; a broker outage therefore delays notifications instead of
 * losing them.
 */
@Entity
@Table(name = "outbox_event")
@Getter
@Setter
@NoArgsConstructor
public class OutboxEvent {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "topic", nullable = false, length = 60)
    private String topic;

    @Column(name = "event_key", nullable = false, length = 64)
    private String eventKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;
}