package com.finova.transaction.integration;

import com.finova.transaction.TransactionServiceApplication;
import com.finova.transaction.repository.ReferenceSequenceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ReferenceGenerator - the real upsert is collision free under concurrency")
@SpringBootTest(classes = TransactionServiceApplication.class)
@ActiveProfiles("test")
class ReferenceGeneratorConcurrencyTest extends PostgresIntegrationTestBase {

    private static final int WORKERS = 4;
    private static final int PER_WORKER = 250;

    @Autowired
    private ReferenceSequenceRepository repository;

    @RepeatedTest(3)
    void shouldHandOutDistinctValuesToConcurrentCallers() throws Exception {
        LocalDate day = LocalDate.of(2026, 10, 1);
        Set<Long> values = new HashSet<>();
        ExecutorService executor = Executors.newFixedThreadPool(WORKERS);
        try {
            List<Callable<List<Long>>> tasks = IntStream.range(0, WORKERS)
                    .mapToObj(worker -> (Callable<List<Long>>) () -> {
                        List<Long> mine = new java.util.ArrayList<>(PER_WORKER);
                        for (int index = 0; index < PER_WORKER; index++) {
                            mine.add(repository.nextValue(day));
                        }
                        return mine;
                    })
                    .toList();
            for (Future<List<Long>> future : executor.invokeAll(tasks)) {
                values.addAll(future.get());
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(WORKERS * PER_WORKER, values.size(), "the database upsert handed out a duplicate value");
        assertEquals(1L, values.stream().mapToLong(Long::longValue).min().orElseThrow());
        assertEquals((long) WORKERS * PER_WORKER, values.stream().mapToLong(Long::longValue).max().orElseThrow());
    }

    @Test
    void shouldResetTheSequenceForANewDay() {
        LocalDate first = LocalDate.of(2026, 10, 2);
        LocalDate second = LocalDate.of(2026, 10, 3);

        assertEquals(1L, repository.nextValue(first));
        assertEquals(1L, repository.nextValue(second));
        assertEquals(2L, repository.nextValue(first));
        assertTrue(repository.existsById(first));
    }
}