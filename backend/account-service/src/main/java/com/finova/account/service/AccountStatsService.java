package com.finova.account.service;

import com.finova.account.dto.AccountStatsResponse;
import com.finova.account.dto.SeriesPoint;
import com.finova.account.repository.AccountRepository;
import com.finova.common.domain.AccountStatus;
import com.finova.common.domain.AccountType;
import com.finova.common.domain.Currency;
import com.finova.common.support.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregations behind the admin dashboard. Every number is derived from the
 * account table, so the figures can never disagree with the list endpoints.
 */
@Service
public class AccountStatsService {

    public static final int SERIES_DAYS = 30;
    private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("dd/MM").withZone(ZoneOffset.UTC);

    private final AccountRepository accountRepository;

    public AccountStatsService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public AccountStatsResponse summary() {
        Map<Currency, BigDecimal> balances = new EnumMap<>(Currency.class);
        for (Currency currency : Currency.values()) {
            balances.put(currency, Money.scale(accountRepository.totalBalanceByCurrency(currency)));
        }
        Map<String, BigDecimal> balancesByCurrency = new LinkedHashMap<>();
        balances.forEach((currency, total) -> balancesByCurrency.put(currency.name(), total));

        Map<String, Long> byType = new LinkedHashMap<>();
        for (AccountType type : AccountType.values()) {
            byType.put(type.name(), 0L);
        }
        for (AccountRepository.AccountTypeCount row : accountRepository.countGroupedByType()) {
            byType.put(row.getAccountType().name(), row.getTotal());
        }

        List<SeriesPoint> growth = new ArrayList<>(SERIES_DAYS);
        List<SeriesPoint> dailyBalances = new ArrayList<>(SERIES_DAYS);
        Map<LocalDay, long[]> buckets = new LinkedHashMap<>();
        Instant start = Instant.now().truncatedTo(ChronoUnit.DAYS).minus(SERIES_DAYS - 1L, ChronoUnit.DAYS);
        for (int day = 0; day < SERIES_DAYS; day++) {
            LocalDay key = new LocalDay(start.plus(day, ChronoUnit.DAYS));
            buckets.put(key, new long[]{0L, 0L});
        }
        Map<LocalDay, BigDecimal> dailyTotals = new LinkedHashMap<>();
        for (AccountRepository.AccountCreatedPoint point : accountRepository.accountsCreatedSince(start)) {
            long[] bucket = buckets.get(keyOf(point.getCreatedAt()));
            if (bucket == null) {
                continue;
            }
            bucket[0]++;
            dailyTotals.merge(keyOf(point.getCreatedAt()), Money.scale(point.getBalance()), BigDecimal::add);
        }
        buckets.forEach((day, bucket) -> {
            growth.add(SeriesPoint.ofCount(day.label(), bucket[0]));
            dailyBalances.add(SeriesPoint.ofTotal(day.label(), dailyTotals.getOrDefault(day, Money.ZERO)));
        });

        return new AccountStatsResponse(
            accountRepository.count(),
            accountRepository.countByStatus(AccountStatus.ACTIVE),
            accountRepository.countByStatus(AccountStatus.BLOCKED),
            accountRepository.countByStatus(AccountStatus.CLOSED),
            balancesByCurrency,
            byType,
            accountRepository.countByCreatedAtAfter(Instant.now().minus(SERIES_DAYS, ChronoUnit.DAYS)),
            growth,
            dailyBalances);
    }

    private static LocalDay keyOf(Instant instant) {
        return new LocalDay(instant.truncatedTo(ChronoUnit.DAYS));
    }

    private record LocalDay(Instant day) {
        String label() {
            return LABEL.format(day);
        }
    }
}
