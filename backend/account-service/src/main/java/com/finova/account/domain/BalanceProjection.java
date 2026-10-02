package com.finova.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Idempotency marker for the balance read projection.
 * <p>
 * One row per {@code transaction.completed} event that has already been applied.
 * The primary key makes a replayed Kafka delivery a no-op instead of a double
 * credit or double debit.
 */
@Entity
@Table(name = "balance_projection")
@Getter
@Setter
@NoArgsConstructor
public class BalanceProjection {

    @Id
    @Column(name = "transaction_id", nullable = false, length = 36)
    private String transactionId;

    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt;

    public BalanceProjection(String transactionId, Instant appliedAt) {
        this.transactionId = transactionId;
        this.appliedAt = appliedAt;
    }
}
