package com.finova.transaction.service;

import com.finova.transaction.repository.ReferenceSequenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ReferenceGenerator - TX-yyyyMMdd-NNNNN uniqueness and format")
class ReferenceGeneratorTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-01T09:15:00Z"), ZoneOffset.UTC);

    /**
     * Mimics the atomic PostgreSQL upsert: one shared counter per day, so two
     * generator instances sharing the repository behave exactly like two service
     * instances hitting the same {@code reference_sequence} row.
     */
    private static final class FakeSequence {
        private final Map<LocalDate, AtomicLong> counters = new HashMap<>();

        synchronized long next(LocalDate date) {
            return counters.computeIfAbsent(date, key -> new AtomicLong()).incrementAndGet();
        }
    }

    private FakeSequence sequence;
    private ReferenceSequenceRepository repository;
    private ReferenceGenerator generator;

    @BeforeEach
    void setUp() {
        sequence = new FakeSequence();
        repository = Mockito.mock(ReferenceSequenceRepository.class);
        Mockito.when(repository.nextValue(Mockito.any(LocalDate.class)))
                .thenAnswer(invocation -> sequence.next(invocation.getArgument(0, LocalDate.class)));
        generator = new ReferenceGenerator(repository, CLOCK);
    }

    @Test
    void shouldMatchTheDocumentedFormat() {
        String reference = generator.next();

        assertTrue(reference.matches("TX-\\d{8}-\\d{5}"), "unexpected reference " + reference);
        assertEquals("TX-20261001-00001", reference);
    }

    @Test
    void shouldProduceTwoThousandUniqueReferences() {
        Set<String> references = new HashSet<>();
        for (int index = 0; index < 2000; index++) {
            String reference = generator.next();
            assertTrue(reference.matches("TX-\\d{8}-\\d{5}"), "unexpected reference " + reference);
            references.add(reference);
        }

        assertEquals(2000, references.size());
    }

    @Test
    void shouldPadTheSequenceToFiveDigits() {
        assertEquals("TX-20261001-00042", generator.format(DAY, 42L));
        assertEquals("TX-20261001-99999", generator.format(DAY, 99999L));
    }

    @Test
    void shouldStartANewSequenceEveryDay() {
        assertEquals("TX-20261001-00001", generator.nextFor(DAY));
        assertEquals("TX-20261002-00001", generator.nextFor(DAY.plusDays(1)));
        assertEquals("TX-20261001-00002", generator.nextFor(DAY));
    }

    @Test
    void shouldNotCollideAcrossTwoConcurrentGenerators() throws Exception {
        ReferenceGenerator left = new ReferenceGenerator(repository, CLOCK);
        ReferenceGenerator right = new ReferenceGenerator(repository, CLOCK);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<List<String>>> tasks = List.of(() -> drain(left, 500), () -> drain(right, 500));
            Set<String> references = new HashSet<>();
            for (Future<List<String>> future : executor.invokeAll(tasks)) {
                references.addAll(future.get());
            }
            assertEquals(1000, references.size());
        } finally {
            executor.shutdownNow();
        }
    }

    @RepeatedTest(3)
    void shouldProduceASequenceThatIsStrictlyIncreasing() {
        long previous = 0L;
        for (int index = 0; index < 500; index++) {
            long value = Long.parseLong(generator.next().substring("TX-20261001-".length()));
            assertTrue(value > previous, "sequence went backwards: " + value + " after " + previous);
            previous = value;
        }
    }

    @Test
    void shouldUseTheUtcDayRegardlessOfTheJvmDefaultZone() {
        assertEquals("TX-20261001-00001", generator.next());
    }

    private List<String> drain(ReferenceGenerator target, int count) {
        List<String> references = new ArrayList<>(count);
        IntStream.range(0, count).forEach(index -> references.add(target.next()));
        return references;
    }
}