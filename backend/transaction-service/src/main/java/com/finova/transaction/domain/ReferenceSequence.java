package com.finova.transaction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Per-day counter backing the human readable transfer reference.
 * <p>
 * The value is only ever read through a single atomic
 * {@code INSERT ... ON CONFLICT DO UPDATE ... RETURNING} statement, so the row
 * itself is never loaded into the persistence context and never cached.
 */
@Entity
@Table(name = "reference_sequence")
@Getter
@Setter
@NoArgsConstructor
public class ReferenceSequence {

    @Id
    @Column(name = "sequence_date", nullable = false)
    private LocalDate sequenceDate;

    @Column(name = "last_value", nullable = false)
    private long lastValue;
}