package com.finova.transaction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Consumer-side idempotency record: one row per consumed {@code (topic, eventId)}.
 * <p>
 * The marker is written inside the same transaction as the settlement, so a
 * redelivered Kafka record either finds the marker already committed or takes
 * the pessimistic lock and observes the terminal transaction status. Either way
 * money moves at most once.
 */
@Entity
@Table(name = "transaction_event_marker",
        uniqueConstraints = @UniqueConstraint(name = "uk_transaction_event_marker_topic_event",
                columnNames = {"topic", "event_id"}))
@Getter
@Setter
@NoArgsConstructor
public class TransactionEventMarker {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "topic", nullable = false, length = 60)
    private String topic;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "transaction_id", nullable = false, length = 36)
    private String transactionId;

    @CreationTimestamp
    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}