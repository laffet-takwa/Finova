package com.finova.transaction.service;

import com.finova.common.error.BusinessException;
import com.finova.common.error.ErrorCode;
import com.finova.transaction.repository.ReferenceSequenceRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Produces the human readable transfer reference {@code TX-<yyyyMMdd>-<NNNNN>}.
 * <p>
 * The counter comes from a single atomic PostgreSQL upsert, so the value is
 * unique across instances without any coordination and the reference stays
 * sortable by date and creation order - the property a bank customer expects
 * from a statement line.
 */
@Service
public class ReferenceGenerator {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT);
    private static final int SEQUENCE_WIDTH = 5;

    private final ReferenceSequenceRepository repository;
    private final Clock clock;

    public ReferenceGenerator(ReferenceSequenceRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public String next() {
        return nextFor(LocalDate.now(clock.withZone(ZoneOffset.UTC)));
    }

    public String nextFor(LocalDate sequenceDate) {
        Long value = repository.nextValue(sequenceDate);
        if (value == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "The transfer reference sequence could not be advanced.");
        }
        return format(sequenceDate, value);
    }

    public String format(LocalDate sequenceDate, long value) {
        return "TX-" + DAY.format(sequenceDate) + "-" + String.format(Locale.ROOT, "%0" + SEQUENCE_WIDTH + "d", value);
    }
}