package com.finova.fraud.service;

import com.finova.common.domain.FraudStatus;
import com.finova.common.domain.RiskLevel;
import com.finova.common.event.TransactionEvent;
import com.finova.common.support.Money;
import com.finova.fraud.config.FraudProperties;
import com.finova.fraud.domain.FraudAlert;
import com.finova.fraud.domain.FraudTimelineKey;
import com.finova.fraud.domain.RecipientHistory;
import com.finova.fraud.domain.TimelineStep;
import com.finova.fraud.domain.VelocityWindow;
import com.finova.fraud.event.FraudDecisionPublisher;
import com.finova.fraud.repository.FraudAlertRepository;
import com.finova.fraud.repository.RecipientHistoryRepository;
import com.finova.fraud.service.rules.FraudContext;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The decision point of the transfer flow: score one transaction, persist one assessment, and queue
 * exactly one decision event.
 */
@Service
public class FraudScoringService {

    private static final Logger log = LoggerFactory.getLogger(FraudScoringService.class);

    private static final Set<String> ANALYSIS_STEPS = Set.of(
            FraudTimelineKey.TRANSACTION_CREATED.name(),
            FraudTimelineKey.AMOUNT_VALIDATION.name(),
            FraudTimelineKey.FRAUD_ANALYSIS.name(),
            FraudTimelineKey.RISK_SCORED.name(),
            FraudTimelineKey.ALERT_CREATED.name());

    private final FraudRuleEngine ruleEngine;
    private final FraudAlertRepository alertRepository;
    private final RecipientHistoryRepository recipientRepository;
    private final MongoTemplate mongoTemplate;
    private final FraudDecisionPublisher decisionPublisher;
    private final FraudProperties properties;

    public FraudScoringService(FraudRuleEngine ruleEngine,
                               FraudAlertRepository alertRepository,
                               RecipientHistoryRepository recipientRepository,
                               MongoTemplate mongoTemplate,
                               FraudDecisionPublisher decisionPublisher,
                               FraudProperties properties) {
        this.ruleEngine = ruleEngine;
        this.alertRepository = alertRepository;
        this.recipientRepository = recipientRepository;
        this.mongoTemplate = mongoTemplate;
        this.decisionPublisher = decisionPublisher;
        this.properties = properties;
    }

    /**
     * Evaluates one transfer.
     * <p>
     * Idempotent on {@code transactionId}, which is what makes the transaction-service reconciler
     * safe: republishing {@code transaction.created} for a stuck transfer converges instead of
     * creating a second alert or a conflicting decision.
     */
    public FraudAlert evaluate(TransactionEvent transaction) {
        Money.requirePositive(transaction.amount());
        Instant now = Instant.now();

        FraudAlert existing = alertRepository.findByTransactionId(transaction.transactionId()).orElse(null);
        if (existing != null && isDecisionFinal(existing)) {
            log.info("Skipping re-evaluation of transaction {}: decision already final (status={}, published={})",
                    transaction.transactionId(), existing.getStatus(), existing.isDecisionPublished());
            return existing;
        }

        VelocityWindow window = recordVelocity(transaction, now);
        boolean recipientSeen = wasRecipientSeen(transaction, now);
        FraudAssessment assessment = ruleEngine.assess(FraudContext.of(transaction, window, recipientSeen, now));

        FraudAlert alert = existing != null ? existing : newAssessment(transaction, now);
        applyAssessment(alert, transaction, assessment, now);
        rememberRecipient(transaction, now);
        alertRepository.save(alert);

        log.info("Scored transaction {} amount {} {} -> score {} risk {} rules {}",
                transaction.transactionId(), alert.getAmount(), alert.getCurrency(),
                alert.getRiskScore(), alert.getRiskLevel(), alert.getTriggeredRules());

        if (!alert.isDecisionPublished()) {
            decisionPublisher.publishDecision(alert, transaction);
            alert.setDecisionPublished(true);
            alert.setUpdatedAt(now);
            alertRepository.save(alert);
        }
        return alert;
    }

    private boolean isDecisionFinal(FraudAlert alert) {
        return alert.isDecisionPublished() || FraudStatus.SAFE.name().equals(alert.getStatus())
                || FraudStatus.CONFIRMED.name().equals(alert.getStatus());
    }

    private FraudAlert newAssessment(TransactionEvent transaction, Instant now) {
        return FraudAlert.builder()
                .id(UUID.randomUUID().toString())
                .transactionId(transaction.transactionId())
                .reference(transaction.reference())
                .senderAccountId(transaction.senderAccountId())
                .receiverAccountId(transaction.receiverAccountId())
                .senderAccountNumber(transaction.senderAccountNumber())
                .senderUserId(transaction.senderUserId())
                .amount(Money.scale(transaction.amount()))
                .currency(transaction.currency())
                .createdAt(now)
                .updatedAt(now)
                .decisionPublished(false)
                .reasons(new ArrayList<>())
                .triggeredRules(new ArrayList<>())
                .timeline(new ArrayList<>())
                .build();
    }

    private void applyAssessment(FraudAlert alert, TransactionEvent transaction,
                                 FraudAssessment assessment, Instant now) {
        alert.setAmount(Money.scale(transaction.amount()));
        alert.setRiskScore(assessment.score());
        alert.setRiskLevel(assessment.level().name());
        alert.setReasons(new ArrayList<>(assessment.reasons()));
        alert.setTriggeredRules(new ArrayList<>(assessment.triggeredRules()));
        alert.setUpdatedAt(now);

        boolean openAlert = assessment.level() == RiskLevel.HIGH;
        alert.setStatus(openAlert ? FraudStatus.OPEN.name() : FraudStatus.SAFE.name());
        if (!openAlert && alert.getReviewedAt() == null) {
            alert.setReviewedAt(now);
        }

        List<TimelineStep> timeline = new ArrayList<>();
        alert.timelineOrEmpty().stream()
                .filter(step -> step.getAt() == null || !ANALYSIS_STEPS.contains(step.getKey()))
                .forEach(timeline::add);
        timeline.addAll(analysisTimeline(transaction, assessment, openAlert, now));
        alert.setTimeline(timeline);
    }

    private List<TimelineStep> analysisTimeline(TransactionEvent transaction, FraudAssessment assessment,
                                               boolean openAlert, Instant now) {
        List<TimelineStep> steps = new ArrayList<>();
        steps.add(TimelineStep.at(FraudTimelineKey.TRANSACTION_CREATED.name(),
                FraudTimelineKey.TRANSACTION_CREATED.label(),
                FraudTimelineKey.TRANSACTION_CREATED.description(), now));
        steps.add(TimelineStep.at(FraudTimelineKey.AMOUNT_VALIDATION.name(),
                FraudTimelineKey.AMOUNT_VALIDATION.label(),
                "Amount " + Money.scale(transaction.amount()) + " " + transaction.currency()
                        + " is positive and normalised to three decimals.", now));
        steps.add(TimelineStep.at(FraudTimelineKey.FRAUD_ANALYSIS.name(),
                FraudTimelineKey.FRAUD_ANALYSIS.label(),
                describeRules(assessment), now));
        steps.add(TimelineStep.at(FraudTimelineKey.RISK_SCORED.name(),
                FraudTimelineKey.RISK_SCORED.label(),
                "Score " + assessment.score() + "/100 (" + assessment.level() + ") from "
                        + assessment.triggeredRules().size() + " triggered rule(s): base "
                        + assessment.baseScore() + ", corroboration +" + assessment.bonus() + ".", now));
        if (openAlert) {
            steps.add(TimelineStep.at(FraudTimelineKey.ALERT_CREATED.name(),
                    FraudTimelineKey.ALERT_CREATED.label(),
                    FraudTimelineKey.ALERT_CREATED.description(), now));
        }
        return steps;
    }

    private String describeRules(FraudAssessment assessment) {
        if (assessment.triggeredRules().isEmpty()) {
            return "No rule triggered; the transfer matches the sender's normal behaviour.";
        }
        return "Rules evaluated: " + ruleEngine.rules().size() + ". Triggered: "
                + String.join(", ", assessment.triggeredRules()) + ".";
    }

    /**
     * Counts this transfer in the sender's velocity window and returns the refreshed snapshot the
     * rules are evaluated against.
     * <p>
     * The counter must never lose an event, even when two transfers for the same account are
     * evaluated at the same instant by different consumers, so the update is a {@code findAndModify}:
     * <ol>
     *   <li>A conditional reset that only matches a window whose {@code windowStart} has already
     *       fallen outside the configured window. {@code findAndModify} re-evaluates the filter under
     *       the document lock, so the reset is applied exactly once and a concurrent thread that no
     *       longer matches simply skips it.</li>
     *   <li>An unconditional {@code $inc} / {@code $push}. Two concurrent increments are serialised
     *       by MongoDB, so neither can overwrite the other.</li>
     * </ol>
     * There is deliberately no upsert on either step: an upsert whose filter does not match would try
     * to insert a second document with the same {@code _id} and fail with a duplicate key. A missing
     * document is created by an explicit insert instead, and the duplicate key that a concurrent
     * creation can provoke is resolved by replaying the increment.
     */
    private VelocityWindow recordVelocity(TransactionEvent transaction, Instant now) {
        String currency = transaction.currency() == null ? "TND" : transaction.currency();
        String id = VelocityWindow.idFor(transaction.senderAccountId(), currency);
        BigDecimal amount = Money.scale(transaction.amount());
        int historySize = properties.getVelocityState().getHistorySize();
        Instant cutoff = now.minusSeconds(properties.getVelocity().getWindowSeconds());

        resetExpiredWindow(id, cutoff, now);
        VelocityWindow updated = applyIncrement(transaction, id, currency, amount, historySize, now);
        if (updated != null) {
            return updated;
        }

        VelocityWindow fresh = VelocityWindow.builder()
                .id(id)
                .senderAccountId(transaction.senderAccountId())
                .senderUserId(transaction.senderUserId())
                .senderAccountNumber(transaction.senderAccountNumber())
                .currency(currency)
                .windowStart(now)
                .firstSeenAt(now)
                .updatedAt(now)
                .eventCount(1)
                .totalAmount(amount)
                .transfersSeen(1)
                .build();
        fresh.remember(amount, historySize, now);
        try {
            return mongoTemplate.insert(fresh);
        } catch (DuplicateKeyException createdByPeer) {
            log.debug("Velocity window {} was created concurrently, replaying the increment", id);
            return applyIncrement(transaction, id, currency, amount, historySize, now);
        }
    }

    private VelocityWindow applyIncrement(TransactionEvent transaction, String id, String currency,
                                          BigDecimal amount, int historySize, Instant now) {
        Update increment = new Update()
                .inc("eventCount", 1)
                .inc("totalAmount", new Decimal128(amount))
                .inc("transfersSeen", 1)
                .set("senderAccountId", transaction.senderAccountId())
                .set("senderUserId", transaction.senderUserId())
                .set("senderAccountNumber", transaction.senderAccountNumber())
                .set("currency", currency)
                .set("updatedAt", now)
                .push("recentAmounts", new Document("$each", List.of(amount))
                        .append("$position", 0)
                        .append("$slice", historySize));
        return mongoTemplate.findAndModify(Query.query(Criteria.where("_id").is(id)), increment,
                FindAndModifyOptions.options().returnNew(true), VelocityWindow.class);
    }

    private void resetExpiredWindow(String id, Instant cutoff, Instant now) {
        mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(id).and("windowStart").lte(cutoff)),
                Update.update("windowStart", now)
                        .set("eventCount", 0)
                        .set("totalAmount", new Decimal128(Money.ZERO)),
                FindAndModifyOptions.options(),
                VelocityWindow.class);
    }

    private boolean wasRecipientSeen(TransactionEvent transaction, Instant now) {
        String receiver = transaction.receiverAccountId();
        if (receiver == null || receiver.isBlank() || transaction.senderAccountId() == null) {
            return true;
        }
        RecipientHistory history = recipientRepository
                .findById(RecipientHistory.idFor(transaction.senderAccountId(), receiver)).orElse(null);
        if (history == null) {
            return false;
        }
        return history.seenSince(now.minus(properties.getNewRecipient().getLookbackDays(), ChronoUnit.DAYS));
    }

    private void rememberRecipient(TransactionEvent transaction, Instant now) {
        String receiver = transaction.receiverAccountId();
        if (receiver == null || receiver.isBlank() || transaction.senderAccountId() == null) {
            return;
        }
        String id = RecipientHistory.idFor(transaction.senderAccountId(), receiver);
        recipientRepository.findById(id).ifPresentOrElse(
                history -> {
                    history.setLastTransferredAt(now);
                    history.setTransferCount(history.getTransferCount() + 1);
                    recipientRepository.save(history);
                },
                () -> recipientRepository.save(RecipientHistory.builder()
                        .id(id)
                        .senderAccountId(transaction.senderAccountId())
                        .receiverAccountId(receiver)
                        .firstTransferredAt(now)
                        .lastTransferredAt(now)
                        .transferCount(1)
                        .build()));
    }
}
