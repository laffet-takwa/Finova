package com.finova.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * The platform system of record for sensitive operations.
 * <p>
 * Append-only: this service never updates or deletes a row on the request path.
 * Entries arrive from two directions and both are idempotent — this service writes
 * a row when it handles a sign-in, then publishes the same fact to
 * {@code audit.recorded} for any downstream reader, while a single consumer reads
 * the other services' events back in. {@code eventId} carries the Kafka envelope id
 * and is protected by the partial unique index {@code uq_audit_logs_event_id}, so a
 * redelivered event can never produce a second copy of an entry.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "event_id", unique = true, length = 64)
    private String eventId;

    @Column(name = "action", nullable = false, length = 40)
    private String action;

    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(name = "resource", nullable = false, length = 60)
    private String resource;

    @Column(name = "resource_id", length = 80)
    private String resourceId;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "result", nullable = false, length = 20)
    private String result;

    @Column(name = "service", nullable = false, length = 40)
    private String service;

    @Column(name = "message", length = 500)
    private String message;

    /**
     * Free-form context kept as JSONB so the admin screen can render a filterable
     * key set without a migration for every new fact. Values are strings only: an
     * audit payload is never allowed to carry a credential or a balance.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, String> metadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
