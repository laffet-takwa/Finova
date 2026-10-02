package com.finova.fraud.service;

import com.finova.common.domain.FraudStatus;
import com.finova.common.domain.RiskLevel;
import com.finova.fraud.domain.FraudAlert;
import com.finova.fraud.dto.FraudStatsResponse;
import com.finova.fraud.repository.FraudAlertRepository;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dashboard aggregates for the fraud console (client spec section 35).
 * <p>
 * Risk bands are counted over the unresolved backlog rather than all time, because that is the
 * question an administrator actually asks: "what is waiting on me right now, and how bad is it".
 */
@Service
public class FraudStatsService {

    private static final int DAILY_WINDOW_DAYS = 30;
    private static final int TOP_ACCOUNTS = 5;
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private static final List<String> UNRESOLVED = List.of(
            FraudStatus.OPEN.name(), FraudStatus.UNDER_REVIEW.name());
    private static final List<String> RESOLVED = List.of(
            FraudStatus.SAFE.name(), FraudStatus.CONFIRMED.name());

    private final FraudAlertRepository alertRepository;
    private final MongoTemplate mongoTemplate;

    public FraudStatsService(FraudAlertRepository alertRepository, MongoTemplate mongoTemplate) {
        this.alertRepository = alertRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public FraudStatsResponse summary() {
        Instant todayStart = LocalDate.now(ZONE).atStartOfDay(ZONE).toInstant();
        Instant dailyFrom = todayStart.minus(DAILY_WINDOW_DAYS - 1L, ChronoUnit.DAYS);

        long openAlerts = alertRepository.countByStatus(FraudStatus.OPEN.name());
        long highRisk = alertRepository.countByRiskLevelAndStatusIn(RiskLevel.HIGH.name(), UNRESOLVED);
        long mediumRisk = alertRepository.countByRiskLevelAndStatusIn(RiskLevel.MEDIUM.name(), UNRESOLVED);
        long lowRisk = alertRepository.countByRiskLevelAndStatusIn(RiskLevel.LOW.name(), UNRESOLVED);
        long resolvedToday = alertRepository.countByUpdatedAtGreaterThanEqualAndStatusIn(todayStart, RESOLVED);
        long confirmedToday = alertRepository.countByUpdatedAtGreaterThanEqualAndStatus(todayStart,
                FraudStatus.CONFIRMED.name());
        long assessedToday = alertRepository.countByCreatedAtGreaterThanEqual(todayStart);

        return new FraudStatsResponse(
                openAlerts,
                highRisk,
                mediumRisk,
                lowRisk,
                resolvedToday,
                confirmedToday,
                assessedToday,
                averageRiskScore(todayStart),
                riskDistribution(),
                statusDistribution(),
                dailyAlerts(dailyFrom),
                topRiskyAccounts());
    }

    private double averageRiskScore(Instant todayStart) {
        List<FraudAlert> assessed = mongoTemplate.find(new Query(Criteria.where("createdAt").gte(todayStart)),
                FraudAlert.class);
        if (assessed.isEmpty()) {
            return 0d;
        }
        double total = assessed.stream().mapToInt(FraudAlert::getRiskScore).sum();
        return Math.round((total / assessed.size()) * 10d) / 10d;
    }

    private Map<String, Long> riskDistribution() {
        Map<String, Long> distribution = new LinkedHashMap<>();
        for (RiskLevel level : RiskLevel.values()) {
            distribution.put(level.name(), alertRepository.countByRiskLevelAndStatusIn(level.name(), UNRESOLVED));
        }
        return distribution;
    }

    private Map<String, Long> statusDistribution() {
        Map<String, Long> distribution = new LinkedHashMap<>();
        for (FraudStatus status : FraudStatus.values()) {
            distribution.put(status.name(), alertRepository.countByStatus(status.name()));
        }
        return distribution;
    }

    /** Zero-filled so the chart never shows a gap for a day without alerts. */
    private List<FraudStatsResponse.SeriesPointResponse> dailyAlerts(Instant from) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("createdAt").gte(from)),
                Aggregation.project("day").andExpression("{$dateToString: {format: '%Y-%m-%d', date: '$createdAt'}}").as("day"),
                Aggregation.group("day").count().as("count"));

        AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, "fraud_alerts",
                Document.class);
        Map<String, Long> counts = new LinkedHashMap<>();
        results.getMappedResults().forEach(row -> counts.put(row.getString("day"),
                ((Number) row.get("count")).longValue()));

        List<FraudStatsResponse.SeriesPointResponse> series = new ArrayList<>();
        LocalDate day = LocalDate.ofInstant(from, ZONE);
        for (int i = 0; i < DAILY_WINDOW_DAYS; i++) {
            String label = day.toString();
            series.add(new FraudStatsResponse.SeriesPointResponse(label, counts.getOrDefault(label, 0L)));
            day = day.plusDays(1);
        }
        return series;
    }

    private List<FraudStatsResponse.TopRiskyAccountResponse> topRiskyAccounts() {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("riskLevel").is(RiskLevel.HIGH.name())),
                Aggregation.group("senderAccountId")
                        .count().as("count")
                        .max("riskScore").as("maxRiskScore")
                        .first("senderAccountNumber").as("senderAccountNumber"),
                Aggregation.sort(Sort.by(Sort.Direction.DESC, "count")),
                Aggregation.limit(TOP_ACCOUNTS));

        AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, "fraud_alerts",
                Document.class);
        List<FraudStatsResponse.TopRiskyAccountResponse> accounts = new ArrayList<>();
        for (Document row : results.getMappedResults()) {
            accounts.add(new FraudStatsResponse.TopRiskyAccountResponse(
                    row.getString("_id"),
                    row.getString("senderAccountNumber"),
                    ((Number) row.get("count")).longValue(),
                    row.get("maxRiskScore") == null ? 0
                            : ((Number) row.get("maxRiskScore")).intValue()));
        }
        return accounts;
    }
}
