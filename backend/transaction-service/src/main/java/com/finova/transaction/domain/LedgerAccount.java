package com.finova.transaction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The authoritative balance of one Finova account.
 * <p>
 * The account-service owns the product (holder, type, status) and keeps a read
 * projection of the balance for the UI; this row is the single source of truth
 * for money. It carries both an optimistic {@code version} and is locked with
 * {@code PESSIMISTIC_WRITE} during settlement, which is the check that is
 * actually effective.
 */
@Entity
@Table(name = "ledger_account")
@Getter
@Setter
@NoArgsConstructor
public class LedgerAccount {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "account_id", nullable = false, length = 36, unique = true)
    private String accountId;

    @Column(name = "account_number", nullable = false, length = 34)
    private String accountNumber;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "account_type", nullable = false, length = 20)
    private String accountType;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "balance", nullable = false, precision = 19, scale = 3)
    private BigDecimal balance;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /**
     * Primitive on purpose: Hibernate seeds a null wrapper version with null, which
     * would violate the NOT NULL constraint on insert.
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}