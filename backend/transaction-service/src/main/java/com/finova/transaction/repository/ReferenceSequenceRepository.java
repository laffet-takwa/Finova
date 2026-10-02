package com.finova.transaction.repository;

import com.finova.transaction.domain.ReferenceSequence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface ReferenceSequenceRepository extends JpaRepository<ReferenceSequence, LocalDate> {

    /**
     * Atomically bumps and returns the per-day counter.
     * <p>
     * PostgreSQL evaluates {@code ON CONFLICT DO UPDATE} under a row lock on the
     * conflicting row, so concurrent callers are serialised by the database and
     * every caller receives a distinct value. The row is never loaded into the
     * persistence context, which is what keeps the sequence out of the entity
     * cache.
     */
    @Query(value = "INSERT INTO reference_sequence (sequence_date, last_value) VALUES (:sequenceDate, 1) "
            + "ON CONFLICT (sequence_date) DO UPDATE SET last_value = reference_sequence.last_value + 1 "
            + "RETURNING last_value", nativeQuery = true)
    Long nextValue(@Param("sequenceDate") LocalDate sequenceDate);
}