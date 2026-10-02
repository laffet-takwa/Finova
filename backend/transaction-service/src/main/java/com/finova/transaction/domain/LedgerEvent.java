package com.finova.transaction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One leg of a double-entry settlement.
 * <p>
 * Every settled transfer writes exactly two rows, a DEBIT on the sender and a
 * CREDIT on the receiver, each carrying the balance before and after so the
 * ledger can be replayed and reconciled independently of {@code ledger_account}.
 */
@Entity
@Table(name = "ledger_event")
@Getter
@Setter
@NoArgsConstructor
public class LedgerEvent {

    public static final String DIRECTION_DEBIT = "DEBIT";
    public static final String DIRECTION_CREDIT = "CREDIT";

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "transaction_id", nullable = false, length = 36)
    private String transactionId;

    @Column(name = "ledger_account_id", nullable = false, length = 36)
    private String ledgerAccountId;

    @Column(name = "account_id", nullable = false, length = 36)
    private String accountId;

    @Column(name = "direction", nullable = false, length = 10)
    private String direction;

    @Column(name = "amount", nullable = false, precision = 19, scale = 3)
    private BigDecimal amount;

    @Column(name = "balance_before", nullable = false, precision = 19, scale = 3)
    private BigDecimal balanceBefore;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 3)
    private BigDecimal balanceAfter;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /** Defaulted on insert so the seeder's historical booking instants survive. */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "reference", nullable = false, length = 24)
    private String reference;

    @PrePersist
    void stampCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}