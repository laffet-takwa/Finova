package com.finova.transaction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A money movement instruction and its lifecycle.
 * <p>
 * A transfer is always inserted as {@code PENDING}; the fraud service decides,
 * and settlement moves the money to a terminal state. {@code idempotencyKey}
 * plus {@code requestFingerprint} are the replay contract, {@code reference} is
 * the human readable, sortable identity handed to the customer.
 */
@Entity
@Table(name = "transaction")
@Getter
@Setter
@NoArgsConstructor
public class Transaction {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "reference", nullable = false, length = 24, unique = true)
    private String reference;

    @Column(name = "idempotency_key", nullable = false, length = 80, unique = true)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Column(name = "sender_account_id", nullable = false, length = 36)
    private String senderAccountId;

    @Column(name = "receiver_account_id", nullable = false, length = 36)
    private String receiverAccountId;

    @Column(name = "sender_account_number", length = 34)
    private String senderAccountNumber;

    @Column(name = "receiver_account_number", length = 34)
    private String receiverAccountNumber;

    @Column(name = "sender_user_id", nullable = false, length = 36)
    private String senderUserId;

    @Column(name = "receiver_user_id", length = 36)
    private String receiverUserId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 3)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "fee", nullable = false, precision = 19, scale = 3)
    private BigDecimal fee;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "type", nullable = false, length = 20)
    private String type;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "risk_score")
    private Integer riskScore;

    @Column(name = "risk_level", length = 10)
    private String riskLevel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "risk_reasons", columnDefinition = "jsonb")
    private List<String> riskReasons;

    /**
     * Defaulted in {@code @PrePersist} rather than by {@code @CreationTimestamp}:
     * the demo seeder books a 45 day history and a generated timestamp would
     * collapse every leg of it onto today, making the dashboard series useless.
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "settled_sender_balance", precision = 19, scale = 3)
    private BigDecimal settledSenderBalance;

    @Column(name = "settled_receiver_balance", precision = 19, scale = 3)
    private BigDecimal settledReceiverBalance;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "requested_by_user_id", length = 36)
    private String requestedByUserId;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    /**
     * Primitive on purpose: Hibernate seeds a null wrapper version with null, which
     * would violate the NOT NULL constraint on insert.
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @PrePersist
    void stampCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}