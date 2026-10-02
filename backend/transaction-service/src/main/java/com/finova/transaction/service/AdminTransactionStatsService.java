package com.finova.transaction.service;

import com.finova.common.domain.TransactionStatus;
import com.finova.common.support.Money;
import com.finova.transaction.domain.Transaction;
import com.finova.transaction.dto.AdminTransactionStatsResponse;
import com.finova.transaction.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Platform-wide statistics for the admin console.
 * <p>
 * Status and type distributions are aggregated by the database; the hourly and
 * daily series are bucketed in memory from a bounded 24h/30d window so the whole
 * screen stays a handful of indexed range scans.
 */
@Service
@Transactional(readOnly = true)
public class AdminTransactionStatsService {

    private static final int HOURLY_BUCKETS = 24;
    private static final int DAILY_BUCKETS = 30;
    private static final int SCALE = 2;

    private final TransactionRepository repository;
    private final Clock clock;

    public AdminTransactionStatsService(TransactionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public AdminTransactionStatsResponse stats() {
        Instant now = Instant.now(clock);
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        Instant startOfToday = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant startOfTomorrow = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        long total = repository.count();
        long completedToday = repository.countByStatusAndCompletedAtAfter(TransactionStatus.COMPLETED, startOfToday);
        long failedToday = repository.countByStatusAndCompletedAtAfter(TransactionStatus.FAILED, startOfToday);
        long pending = repository.countByStatus(TransactionStatus.PENDING);
        long flagged = repository.countByStatus(TransactionStatus.FLAGGED);
        BigDecimal successRate = successRate(completedToday, failedToday);

        List<Transaction> todayCompleted = repository.findCompletedBetween(startOfToday, startOfTomorrow);
        BigDecimal volumeToday = total(todayCompleted);

        Map<String, BigDecimal> volumeByCurrency = new LinkedHashMap<>();
        for (Object[] row : repository.volumeByCurrencySince(startOfToday)) {
            volumeByCurrency.put(String.valueOf(row[0]), orZero((BigDecimal) row[1]));
        }

        return new AdminTransactionStatsResponse(
                total,
                completedToday,
                failedToday,
                pending,
                flagged,
                successRate,
                volumeToday,
                volumeByCurrency,
                distribution(repository.countGroupByStatus()),
                distribution(repository.countGroupByType()),
                hourlyVolume(now),
                dailyVolume(today),
                averageAmount(),
                largestAmount());
    }

    private List<AdminTransactionStatsResponse.Bucket> hourlyVolume(Instant now) {
        Instant firstBucket = now.truncatedTo(ChronoUnit.HOURS).minus(Duration.ofHours(HOURLY_BUCKETS - 1L));
        List<Transaction> completed = repository.findCompletedBetween(firstBucket, now.plus(Duration.ofMinutes(1)));
        return bucket(completed, HOURLY_BUCKETS, index -> firstBucket.plus(Duration.ofHours(index)),
                Duration.ofHours(1));
    }

    private List<AdminTransactionStatsResponse.Bucket> dailyVolume(LocalDate today) {
        LocalDate firstDay = today.minusDays(DAILY_BUCKETS - 1L);
        Instant firstBucket = firstDay.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<Transaction> completed = repository.findCompletedBetween(firstBucket, end);
        return bucket(completed, DAILY_BUCKETS,
                index -> firstDay.plusDays(index).atStartOfDay(ZoneOffset.UTC).toInstant(),
                Duration.ofDays(1));
    }

    private List<AdminTransactionStatsResponse.Bucket> bucket(List<Transaction> completed, int bucketCount,
                                                             java.util.function.IntFunction<Instant> start,
                                                             Duration width) {
        Instant[] starts = new Instant[bucketCount];
        long[] counts = new long[bucketCount];
        BigDecimal[] volumes = new BigDecimal[bucketCount];
        for (int index = 0; index < bucketCount; index++) {
            starts[index] = start.apply(index);
            counts[index] = 0L;
            volumes[index] = Money.ZERO;
        }
        for (Transaction transaction : completed) {
            Instant completedAt = transaction.getCompletedAt();
            if (completedAt == null || transaction.getAmount() == null) {
                continue;
            }
            for (int index = bucketCount - 1; index >= 0; index--) {
                if (completedAt.isBefore(starts[index]) || !completedAt.isBefore(starts[index].plus(width))) {
                    continue;
                }
                counts[index]++;
                volumes[index] = Money.scale(volumes[index].add(transaction.getAmount()));
                break;
            }
        }
        List<AdminTransactionStatsResponse.Bucket> buckets = new ArrayList<>(bucketCount);
        for (int index = 0; index < bucketCount; index++) {
            buckets.add(new AdminTransactionStatsResponse.Bucket(starts[index], starts[index].plus(width),
                    counts[index], volumes[index]));
        }
        return buckets;
    }

    private BigDecimal successRate(long completed, long failed) {
        long decided = completed + failed;
        if (decided == 0) {
            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_EVEN);
        }
        return BigDecimal.valueOf(completed).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(decided), SCALE, RoundingMode.HALF_EVEN);
    }

    private BigDecimal total(List<Transaction> transactions) {
        return transactions.stream()
                .map(Transaction::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(Money.ZERO, (left, right) -> Money.scale(left.add(right)));
    }

    private Map<String, Long> distribution(List<Object[]> rows) {
        Map<String, Long> distribution = new LinkedHashMap<>();
        for (Object[] row : rows) {
            distribution.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }
        return distribution;
    }

    private BigDecimal averageAmount() {
        Object[] row = repository.averageAndLargestCompletedAmount();
        if (row == null || row.length == 0) {
            return Money.ZERO;
        }
        return orZero((BigDecimal) row[0]);
    }

    private BigDecimal largestAmount() {
        Object[] row = repository.averageAndLargestCompletedAmount();
        if (row == null || row.length < 2) {
            return Money.ZERO;
        }
        return orZero((BigDecimal) row[1]);
    }

    private BigDecimal orZero(BigDecimal value) {
        return value == null ? Money.ZERO : Money.scale(value);
    }
}