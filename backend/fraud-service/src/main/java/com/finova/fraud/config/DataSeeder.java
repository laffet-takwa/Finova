package com.finova.fraud.config;

import com.finova.common.domain.FraudStatus;
import com.finova.common.domain.RiskLevel;
import com.finova.common.support.Money;
import com.finova.fraud.domain.FraudAlert;
import com.finova.fraud.domain.FraudTimelineKey;
import com.finova.fraud.domain.RecipientHistory;
import com.finova.fraud.domain.TimelineStep;
import com.finova.fraud.domain.VelocityWindow;
import com.finova.fraud.repository.FraudAlertRepository;
import com.finova.fraud.repository.RecipientHistoryRepository;
import com.finova.fraud.repository.VelocityWindowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Realistic demo backlog for the admin console, dev profile only.
 * <p>
 * Users and accounts are owned by other services, so the identifiers here are opaque strings the
 * console merely displays. The seeder is idempotent: it does nothing when alerts already exist.
 */
@Component
@Profile("dev")
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private static final String[] CURRENCIES = {"TND", "TND", "TND", "EUR", "USD"};
    private static final int ALERT_COUNT = 34;
    private static final int RESOLVED_TODAY = 11;

    private final FraudAlertRepository alertRepository;
    private final VelocityWindowRepository windowRepository;
    private final RecipientHistoryRepository recipientRepository;

    public DataSeeder(FraudAlertRepository alertRepository,
                      VelocityWindowRepository windowRepository,
                      RecipientHistoryRepository recipientRepository) {
        this.alertRepository = alertRepository;
        this.windowRepository = windowRepository;
        this.recipientRepository = recipientRepository;
    }

    @Override
    public void run(String... args) {
        if (alertRepository.count() > 0) {
            log.info("Fraud demo data already present, skipping the seeder");
            return;
        }
        Random random = new Random(20260901L);
        Instant now = Instant.now();
        Instant todayStart = LocalDate.now(ZoneId.systemDefault()).atStartOfDay(ZoneId.systemDefault())
                .toInstant();

        List<FraudAlert> alerts = new ArrayList<>();
        for (int i = 0; i < ALERT_COUNT; i++) {
            alerts.add(buildAlert(i, random, now, todayStart));
        }
        alertRepository.saveAll(alerts);
        seedVelocityWindows(random, now);
        seedRecipientHistory(random, now);
        printSummary(alerts, now);
    }

    private FraudAlert buildAlert(int index, Random random, Instant now, Instant todayStart) {
        String senderAccountId = "acc-" + (1000 + random.nextInt(40));
        String senderUserId = "usr-" + (2000 + random.nextInt(40));
        String senderAccountNumber = "TN" + String.format("%08d", 10000000 + random.nextInt(89999999));
        String receiverAccountId = "acc-" + (2000 + random.nextInt(60));
        String currency = CURRENCIES[random.nextInt(CURRENCIES.length)];

        int riskScore = pickRiskScore(random, index);
        RiskLevel riskLevel = RiskLevel.fromScore(riskScore);
        BigDecimal amount = pickAmount(random, currency, riskScore);
        Instant createdAt = pickCreatedAt(random, now, todayStart, index);
        boolean resolvedToday = index < RESOLVED_TODAY;
        FraudStatus status = resolveStatus(riskLevel, resolvedToday, random);

        FraudAlert alert = FraudAlert.builder()
                .id(UUID.randomUUID().toString())
                .transactionId("txn-" + UUID.randomUUID())
                .reference(reference(createdAt, index))
                .senderAccountId(senderAccountId)
                .receiverAccountId(receiverAccountId)
                .senderAccountNumber(senderAccountNumber)
                .senderUserId(senderUserId)
                .amount(amount)
                .currency(currency)
                .riskScore(riskScore)
                .riskLevel(riskLevel.name())
                .reasons(new ArrayList<>(reasonsFor(riskScore, amount, currency)))
                .triggeredRules(new ArrayList<>(rulesFor(riskScore)))
                .status(status.name())
                .createdAt(createdAt)
                .updatedAt(resolvedToday ? now.minusSeconds(random.nextInt(3600)) : createdAt)
                .decisionPublished(true)
                .build();

        if (status != FraudStatus.OPEN) {
            alert.setReviewedAt(alert.getUpdatedAt());
            alert.setReviewedBy("usr-0001");
            alert.setReviewNote(reviewNote(status));
        }

        List<TimelineStep> timeline = new ArrayList<>(analysisTimeline(alert, createdAt));
        if (status != FraudStatus.OPEN) {
            timeline.add(TimelineStep.at(FraudTimelineKey.MARKED_SAFE.name(),
                    FraudTimelineKey.MARKED_SAFE.label(), FraudTimelineKey.MARKED_SAFE.description(),
                    alert.getUpdatedAt()));
        }
        alert.setTimeline(timeline);
        return alert;
    }

    private List<TimelineStep> analysisTimeline(FraudAlert alert, Instant createdAt) {
        List<TimelineStep> steps = new ArrayList<>();
        steps.add(TimelineStep.at(FraudTimelineKey.TRANSACTION_CREATED.name(),
                FraudTimelineKey.TRANSACTION_CREATED.label(),
                FraudTimelineKey.TRANSACTION_CREATED.description(), createdAt));
        steps.add(TimelineStep.at(FraudTimelineKey.AMOUNT_VALIDATION.name(),
                FraudTimelineKey.AMOUNT_VALIDATION.label(),
                "Amount " + alert.getAmount() + " " + alert.getCurrency()
                        + " is positive and normalised to three decimals.", createdAt));
        steps.add(TimelineStep.at(FraudTimelineKey.FRAUD_ANALYSIS.name(),
                FraudTimelineKey.FRAUD_ANALYSIS.label(),
                "Rules evaluated: 5. Triggered: " + String.join(", ", alert.getTriggeredRules()) + ".",
                createdAt));
        steps.add(TimelineStep.at(FraudTimelineKey.RISK_SCORED.name(),
                FraudTimelineKey.RISK_SCORED.label(),
                "Score " + alert.getRiskScore() + "/100 (" + alert.getRiskLevel()
                        + ") from " + alert.getTriggeredRules().size() + " triggered rule(s).",
                createdAt));
        if (RiskLevel.HIGH.name().equals(alert.getRiskLevel())) {
            steps.add(TimelineStep.at(FraudTimelineKey.ALERT_CREATED.name(),
                    FraudTimelineKey.ALERT_CREATED.label(),
                    FraudTimelineKey.ALERT_CREATED.description(), createdAt));
        }
        return steps;
    }

    private int pickRiskScore(Random random, int index) {
        if (index < 18) {
            // Backlog: a realistic spread of HIGH, MEDIUM and LOW still waiting on an administrator.
            return switch (index % 3) {
                case 0 -> 71 + random.nextInt(30);
                case 1 -> 35 + random.nextInt(34);
                default -> 3 + random.nextInt(27);
            };
        }
        return switch (random.nextInt(4)) {
            case 0 -> 72 + random.nextInt(25);
            case 1 -> 32 + random.nextInt(35);
            default -> 2 + random.nextInt(28);
        };
    }

    private BigDecimal pickAmount(Random random, String currency, int riskScore) {
        BigDecimal base = switch (currency) {
            case "EUR" -> BigDecimal.valueOf(400 + random.nextInt(2200));
            case "USD" -> BigDecimal.valueOf(500 + random.nextInt(2600));
            default -> BigDecimal.valueOf(1500 + random.nextInt(9000));
        };
        if (riskScore >= 71) {
            base = base.multiply(BigDecimal.valueOf(2));
        }
        return Money.scale(base.add(BigDecimal.valueOf(random.nextInt(999)).movePointLeft(3)));
    }

    private Instant pickCreatedAt(Random random, Instant now, Instant todayStart, int index) {
        if (index < 14) {
            long secondsToday = ChronoUnit.SECONDS.between(todayStart, now);
            return now.minusSeconds(random.nextInt((int) Math.max(1, secondsToday)));
        }
        long daysBack = 1 + random.nextInt(29);
        return now.minus(daysBack, ChronoUnit.DAYS).minus(random.nextInt(86_000), ChronoUnit.SECONDS);
    }

    private FraudStatus resolveStatus(RiskLevel level, boolean resolvedToday, Random random) {
        if (resolvedToday) {
            return random.nextInt(4) == 0 ? FraudStatus.CONFIRMED : FraudStatus.SAFE;
        }
        if (level != RiskLevel.HIGH) {
            return FraudStatus.SAFE;
        }
        return random.nextInt(3) == 0 ? FraudStatus.UNDER_REVIEW : FraudStatus.OPEN;
    }

    private String reviewNote(FraudStatus status) {
        return status == FraudStatus.CONFIRMED
                ? "Confirmed fraud: destination account opened the same day."
                : "Reviewed with the sender, transfer was legitimate.";
    }

    private List<String> rulesFor(int riskScore) {
        List<String> rules = new ArrayList<>();
        if (riskScore >= 71) {
            rules.add("LARGE_AMOUNT");
        }
        if (riskScore >= 31) {
            rules.add("UNUSUAL_PATTERN");
        }
        if (riskScore >= 60) {
            rules.add("BURST_VELOCITY");
        }
        if (rules.isEmpty()) {
            rules.add("LARGE_AMOUNT");
        }
        return rules;
    }

    private List<String> reasonsFor(int riskScore, BigDecimal amount, String currency) {
        List<String> reasons = new ArrayList<>();
        if (riskScore >= 71) {
            reasons.add("Large transaction amount of " + amount + " " + currency
                    + " exceeds the review threshold.");
        }
        if (riskScore >= 31) {
            reasons.add("Amount is 3.7x the sender's average of 420.500 " + currency
                    + " over the last transactions.");
        }
        if (riskScore >= 60) {
            reasons.add("8 transactions from this account within the last 60 seconds.");
        }
        return reasons;
    }

    private String reference(Instant createdAt, int index) {
        return "TX-" + LocalDate.ofInstant(createdAt, ZoneId.systemDefault()).toString().replace("-", "")
                + "-" + String.format("%05d", 10000 + index * 37);
    }

    private void seedVelocityWindows(Random random, Instant now) {
        List<VelocityWindow> windows = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            String accountId = "acc-" + (1000 + i);
            List<BigDecimal> amounts = new ArrayList<>();
            for (int j = 0; j < 20; j++) {
                amounts.add(Money.scale(BigDecimal.valueOf(150 + random.nextInt(900))
                        .add(BigDecimal.valueOf(random.nextInt(999)).movePointLeft(3))));
            }
            windows.add(VelocityWindow.builder()
                    .id(VelocityWindow.idFor(accountId, "TND"))
                    .senderAccountId(accountId)
                    .senderUserId("usr-" + (2000 + i))
                    .senderAccountNumber("TN" + String.format("%08d", 10000000 + i * 7717))
                    .currency("TND")
                    .windowStart(now.minusSeconds(random.nextInt(40)))
                    .eventCount(1 + random.nextInt(11))
                    .totalAmount(Money.scale(BigDecimal.valueOf(800 + random.nextInt(9000))))
                    .recentAmounts(amounts)
                    .transfersSeen(40 + random.nextInt(200))
                    .firstSeenAt(now.minus(90 + random.nextInt(200), ChronoUnit.DAYS))
                    .updatedAt(now)
                    .build());
        }
        windowRepository.saveAll(windows);
    }

    private void seedRecipientHistory(Random random, Instant now) {
        List<RecipientHistory> history = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            history.add(RecipientHistory.builder()
                    .id(RecipientHistory.idFor("acc-" + (1000 + i), "acc-" + (2000 + i)))
                    .senderAccountId("acc-" + (1000 + i))
                    .receiverAccountId("acc-" + (2000 + i))
                    .firstTransferredAt(now.minus(30 + random.nextInt(300), ChronoUnit.DAYS))
                    .lastTransferredAt(now.minus(1 + random.nextInt(20), ChronoUnit.DAYS))
                    .transferCount(1 + random.nextInt(25))
                    .build());
        }
        recipientRepository.saveAll(history);
    }

    private void printSummary(List<FraudAlert> alerts, Instant now) {
        long open = alerts.stream().filter(a -> FraudStatus.OPEN.name().equals(a.getStatus())).count();
        long underReview = alerts.stream()
                .filter(a -> FraudStatus.UNDER_REVIEW.name().equals(a.getStatus())).count();
        long high = alerts.stream()
                .filter(a -> RiskLevel.HIGH.name().equals(a.getRiskLevel())
                        && !FraudStatus.SAFE.name().equals(a.getStatus())
                        && !FraudStatus.CONFIRMED.name().equals(a.getStatus())).count();
        long medium = alerts.stream()
                .filter(a -> RiskLevel.MEDIUM.name().equals(a.getRiskLevel())
                        && !FraudStatus.SAFE.name().equals(a.getStatus())
                        && !FraudStatus.CONFIRMED.name().equals(a.getStatus())).count();
        long low = alerts.stream()
                .filter(a -> RiskLevel.LOW.name().equals(a.getRiskLevel())
                        && !FraudStatus.SAFE.name().equals(a.getStatus())
                        && !FraudStatus.CONFIRMED.name().equals(a.getStatus())).count();
        long safe = alerts.stream().filter(a -> FraudStatus.SAFE.name().equals(a.getStatus())).count();
        long confirmed = alerts.stream()
                .filter(a -> FraudStatus.CONFIRMED.name().equals(a.getStatus())).count();

        StringBuilder block = new StringBuilder();
        block.append(System.lineSeparator())
                .append("=== FINOVA FRAUD DEMO DATA ===").append(System.lineSeparator())
                .append("Fraud alerts seeded : ").append(alerts.size()).append(System.lineSeparator())
                .append("  OPEN               : ").append(open).append(System.lineSeparator())
                .append("  UNDER_REVIEW       : ").append(underReview).append(System.lineSeparator())
                .append("  SAFE               : ").append(safe).append(System.lineSeparator())
                .append("  CONFIRMED          : ").append(confirmed).append(System.lineSeparator())
                .append("Unresolved by risk  : HIGH ").append(high)
                .append(" / MEDIUM ").append(medium)
                .append(" / LOW ").append(low).append(System.lineSeparator())
                .append("Velocity windows    : 6 (live burst state)").append(System.lineSeparator())
                .append("Recipient history   : 10 pairs").append(System.lineSeparator())
                .append("Seeded at           : ").append(now).append(System.lineSeparator())
                .append("=== END FINOVA FRAUD DEMO DATA ===");
        log.info(block.toString());
    }
}