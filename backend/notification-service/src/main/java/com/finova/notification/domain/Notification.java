package com.finova.notification.domain;

import com.finova.common.domain.NotificationType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One row in a user's inbox.
 * <p>
 * {@code sourceEventId} is the dedupe key of the Kafka consumer: it is derived
 * from the source envelope id and the recipient role, and it is protected by the
 * partial unique index {@code uq_notification_source_event}, so a redelivered
 * event can never produce a second copy of an entry.
 * <p>
 * {@code version} guards the read/unread race: two devices marking the same entry
 * read concurrently both succeed, and the loser's update is a no-op rather than a
 * stale overwrite.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 30, nullable = false)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 20, nullable = false)
    private NotificationCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 10, nullable = false)
    private NotificationSeverity severity;

    @Column(name = "title", length = 140, nullable = false)
    private String title;

    @Column(name = "message", length = 500, nullable = false)
    private String message;

    @Column(name = "transaction_id", length = 36)
    private String transactionId;

    @Column(name = "reference", length = 24)
    private String reference;

    @Column(name = "amount", precision = 19, scale = 3)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "read", nullable = false)
    private boolean read;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "source_service", length = 40)
    private String sourceService;

    @Column(name = "source_event_id", length = 64)
    private String sourceEventId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}