package com.finova.transaction.service;

import com.finova.common.support.Money;
import com.finova.transaction.domain.Transaction;
import com.finova.transaction.dto.TransactionSummaryResponse;
import com.finova.transaction.mapper.TransactionMapper;
import com.finova.transaction.repository.TransactionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Customer dashboard aggregates (client spec section 23).
 * <p>
 * A transaction where the caller is the sender is an expense, where the caller is
 * the receiver it is income; only completed transfers count, so a pending or
 * flagged transfer never distorts the monthly trend.
 */
@Service
@Transactional(readOnly = true)
public class TransactionSummaryService {

    private static final int SERIES_DAYS = 30;
    private static final int RECENT_LIMIT = 5;
    private static final int RECENT_PAGE = 50;

    private final TransactionRepository repository;
    private final TransactionMapper mapper;
    private final Clock clock;

    public TransactionSummaryService(TransactionRepository repository, TransactionMapper mapper, Clock clock) {
        this.repository = repository;
        this.mapper = mapper;
        this.clock = clock;
    }

    public TransactionSummaryResponse summarise(String userId) {
        Instant now = Instant.now(clock);
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));

        YearMonth currentMonth = YearMonth.from(today);
        YearMonth previousMonth = currentMonth.minusMonths(1);
        Instant currentFrom = startOfMonth(currentMonth);
        Instant currentTo = startOfMonth(currentMonth.plusMonths(1));
        Instant previousFrom = startOfMonth(previousMonth);
        Instant previousTo = currentFrom;

        BigDecimal income = Money.scale(orZero(repository.sumIncomeForHolder(userId, currentFrom, currentTo)));
        BigDecimal expenses = Money.scale(orZero(repository.sumExpensesForHolder(userId, currentFrom, currentTo)));
        BigDecimal previousIncome = Money.scale(orZero(repository.sumIncomeForHolder(userId, previousFrom, previousTo)));

        BigDecimal absoluteChange = Money.scale(income.subtract(previousIncome));
        BigDecimal percentChange = previousIncome.signum() == 0
                ? null
                : absoluteChange.multiply(BigDecimal.valueOf(100))
                .divide(previousIncome, 2, RoundingMode.HALF_EVEN);

        return new TransactionSummaryResponse(
                income,
                expenses,
                countOf(repository.findCompletedLegsForHolder(userId, currentFrom, currentTo)),
                percentChange,
                absoluteChange,
                dailySeries(userId, today),
                recent(userId));
    }

    private int countOf(List<Object[]> legs) {
        return legs.size();
    }

    private List<TransactionSummaryResponse.DailyPoint> dailySeries(String userId, LocalDate today) {
        LocalDate firstDay = today.minusDays(SERIES_DAYS - 1L);
        Map<LocalDate, Accumulator> buckets = new LinkedHashMap<>();
        for (int day = 0; day < SERIES_DAYS; day++) {
            buckets.put(firstDay.plusDays(day), new Accumulator());
        }
        Instant from = firstDay.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        for (Object[] leg : repository.findCompletedLegsForHolder(userId, from, to)) {
            Instant completedAt = (Instant) leg[0];
            BigDecimal amount = Money.scale((BigDecimal) leg[1]);
            String senderUserId = (String) leg[2];
            String receiverUserId = (String) leg[3];
            if (completedAt == null || amount == null) {
                continue;
            }
            Accumulator accumulator = buckets.get(completedAt.atZone(ZoneOffset.UTC).toLocalDate());
            if (accumulator == null) {
                continue;
            }
            accumulator.count++;
            if (userId.equals(receiverUserId)) {
                accumulator.income = Money.scale(accumulator.income.add(amount));
            }
            if (userId.equals(senderUserId)) {
                accumulator.expenses = Money.scale(accumulator.expenses.add(amount));
            }
        }
        List<TransactionSummaryResponse.DailyPoint> series = new ArrayList<>(SERIES_DAYS);
        buckets.forEach((date, accumulator) -> series.add(new TransactionSummaryResponse.DailyPoint(
                date, accumulator.income, accumulator.expenses, accumulator.count)));
        return series;
    }

    private List<com.finova.transaction.dto.TransactionResponse> recent(String userId) {
        List<Transaction> latest = repository.findBySenderUserIdOrReceiverUserIdOrderByCreatedAtDesc(
                userId, userId, PageRequest.of(0, RECENT_PAGE));
        return mapper.toResponses(latest.subList(0, Math.min(RECENT_LIMIT, latest.size())));
    }

    private Instant startOfMonth(YearMonth month) {
        return month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private BigDecimal orZero(BigDecimal value) {
        return value == null ? Money.ZERO : value;
    }

    private static final class Accumulator {
        private BigDecimal income = Money.ZERO;
        private BigDecimal expenses = Money.ZERO;
        private int count;
    }
}