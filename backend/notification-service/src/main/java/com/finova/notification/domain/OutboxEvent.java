package com.finova.notification.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Transactional outbox row.
 * <p>
 * The inbox row and the {@code notification.created} event must not be able to
 * disagree: if the process died between the database commit and the Kafka send the
 * user would have an entry nobody downstream ever heard about. The event is
 * therefore written here in the same transaction as the notification and drained
 * by a scheduled publisher, which turns "publish" into "at least once, retried"
 * without losing anything.
 * <p>
 * {@code dedupeKey} is unique, so re-draining a row after a crash between the send
 * and the {@code publishedAt} write cannot produce a second copy downstream.
 */
@Entity
@Table(name = "outbox_event")
@Getter
@Setter
@NoArgsConstructor
public class OutboxEvent {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "topic", length = 60, nullable = false)
    private String topic;

    @Column(name = "event_key", length = 80, nullable = false)
    private String eventKey;

    @Column(name = "dedupe_key", length = 160, nullable = false, unique = true)
    private String dedupeKey;

    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 500)
    private String lastError;

    public boolean isPending() {
        return publishedAt == null;
    }
}