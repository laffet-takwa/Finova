package com.finova.fraud.service;

import com.finova.common.domain.AccountStatus;
import com.finova.common.domain.FraudStatus;
import com.finova.common.domain.RiskLevel;
import com.finova.common.error.BusinessException;
import com.finova.common.error.ErrorCode;
import com.finova.common.event.AccountBlockedEvent;
import com.finova.common.event.AuditRecordEvent;
import com.finova.common.event.TransactionEvent;
import com.finova.common.security.CurrentUser;
import com.finova.common.web.CorrelationId;
import com.finova.fraud.client.AccountServiceClient;
import com.finova.fraud.client.AccountStatusUpdateRequest;
import com.finova.fraud.domain.FraudAction;
import com.finova.fraud.domain.FraudAlert;
import com.finova.fraud.domain.FraudTimelineKey;
import com.finova.fraud.domain.TimelineStep;
import com.finova.fraud.dto.FraudAlertFilters;
import com.finova.fraud.dto.FraudAlertResponse;
import com.finova.fraud.event.FraudDecisionPublisher;
import com.finova.fraud.mapper.FraudAlertMapper;
import com.finova.fraud.repository.FraudAlertRepository;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Read access for the fraud console plus the four administrator review actions. */
@Service
public class FraudAlertService {

    private static final Logger log = LoggerFactory.getLogger(FraudAlertService.class);

    /** Statuses an administrator may still act on. */
    private static final List<String> ACTIONABLE = List.of(
            FraudStatus.OPEN.name(), FraudStatus.UNDER_REVIEW.name());

    private final FraudAlertRepository alertRepository;
    private final MongoTemplate mongoTemplate;
    private final AccountServiceClient accountServiceClient;
    private final FraudDecisionPublisher decisionPublisher;

    public FraudAlertService(FraudAlertRepository alertRepository,
                             MongoTemplate mongoTemplate,
                             AccountServiceClient accountServiceClient,
                             FraudDecisionPublisher decisionPublisher) {
        this.alertRepository = alertRepository;
        this.mongoTemplate = mongoTemplate;
        this.accountServiceClient = accountServiceClient;
        this.decisionPublisher = decisionPublisher;
    }

    public Page<FraudAlertResponse> findAll(FraudAlertFilters filters) {
        Pageable pageable = PageRequest.of(filters.page(), filters.size(),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Query query = new Query();
        applyFilters(query, filters);
        long total = mongoTemplate.count(query, FraudAlert.class);
        List<FraudAlertResponse> content = mongoTemplate.find(query.with(pageable), FraudAlert.class)
                .stream().map(FraudAlertMapper::toResponse).toList();
        return PageableExecutionUtils.getPage(content, pageable, () -> total);
    }

    /** OPEN plus UNDER_REVIEW: everything still waiting on an administrator. */
    public List<FraudAlertResponse> findUnresolved() {
        Query query = new Query(Criteria.where("status").in(ACTIONABLE))
                .with(Sort.by(Sort.Direction.DESC, "riskScore")
                        .and(Sort.by(Sort.Direction.DESC, "createdAt")));
        return mongoTemplate.find(query, FraudAlert.class)
                .stream().map(FraudAlertMapper::toResponse).toList();
    }

    public FraudAlertResponse findById(String id) {
        return FraudAlertMapper.toResponse(require(id));
    }

    public FraudAlertResponse findByTransactionId(String transactionId) {
        FraudAlert alert = alertRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.FRAUD_ALERT_NOT_FOUND,
                        "Fraud alert for transaction", transactionId));
        return FraudAlertMapper.toResponse(alert);
    }

    /** Picks the alert up without moving money. */
    public FraudAlertResponse startReview(String id, String note) {
        FraudAlert alert = requireActionable(id);
        Instant now = Instant.now();
        alert.setStatus(FraudStatus.UNDER_REVIEW.name());
        alert.setReviewedBy(CurrentUser.userId());
        alert.setReviewedAt(now);
        alert.setReviewNote(note);
        alert.setUpdatedAt(now);
        alert.appendTimeline(TimelineStep.at(FraudTimelineKey.REVIEW_STARTED.name(),
                FraudTimelineKey.REVIEW_STARTED.label(),
                "Picked up by " + alert.getReviewedBy() + ". No money has moved.", now));
        FraudAlert saved = alertRepository.save(alert);
        audit(saved, FraudAction.REVIEW_STARTED, note,
                "Fraud review started on alert " + saved.getId() + ".");
        return FraudAlertMapper.toResponse(saved);
    }

    /**
     * Marks the alert safe, which releases the held funds.
     * <p>
     * This is the only money-affecting action in the service, so it is guarded twice: the alert must
     * still be actionable, and the release is queued on the outbox rather than sent inline.
     */
    public FraudAlertResponse markSafe(String id, String note) {
        FraudAlert alert = requireActionable(id);
        Instant now = Instant.now();
        alert.setStatus(FraudStatus.SAFE.name());
        alert.setReviewedBy(CurrentUser.userId());
        alert.setReviewedAt(now);
        alert.setReviewNote(note);
        alert.setUpdatedAt(now);
        alert.appendTimeline(TimelineStep.at(FraudTimelineKey.MARKED_SAFE.name(),
                FraudTimelineKey.MARKED_SAFE.label(),
                FraudTimelineKey.MARKED_SAFE.description(), now));
        FraudAlert saved = alertRepository.save(alert);
        decisionPublisher.publishApproval(saved, asTransactionEvent(saved));
        audit(saved, FraudAction.MARKED_SAFE, note,
                "Fraud alert " + saved.getId() + " marked safe, funds released for settlement.");
        return FraudAlertMapper.toResponse(saved);
    }

    /**
     * Confirms the transfer as fraudulent. The funds stay held for manual recovery.
     * <p>
     * Rejecting the transfer is deliberately not done here: the transaction service owns the failure
     * decision path and no event exists for it yet.
     */
    public FraudAlertResponse confirmFraud(String id, String note) {
        FraudAlert alert = requireActionable(id);
        Instant now = Instant.now();
        alert.setStatus(FraudStatus.CONFIRMED.name());
        alert.setReviewedBy(CurrentUser.userId());
        alert.setReviewedAt(now);
        alert.setReviewNote(note);
        alert.setUpdatedAt(now);
        alert.appendTimeline(TimelineStep.at(FraudTimelineKey.CONFIRMED_FRAUD.name(),
                FraudTimelineKey.CONFIRMED_FRAUD.label(),
                FraudTimelineKey.CONFIRMED_FRAUD.description(), now));
        FraudAlert saved = alertRepository.save(alert);
        audit(saved, FraudAction.CONFIRMED_FRAUD, note,
                "Fraud alert " + saved.getId() + " confirmed as fraudulent.");
        return FraudAlertMapper.toResponse(saved);
    }

    /**
     * Blocks the sender account and confirms the alert.
     * <p>
     * The alert is only confirmed once the account service has accepted the block: a silent failure
     * would leave the account open while the console claims it was closed. An account that is already
     * blocked is not an error, so a conflict from the account service is treated as success.
     */
    public FraudAlertResponse blockAccount(String id, String note) {
        FraudAlert alert = requireActionable(id);
        String reason = "Fraud review: transfer " + alert.getReference() + " assessed at "
                + alert.getRiskScore() + "/100."
                + (note == null ? "" : " " + note);
        blockSenderAccount(alert, reason);

        Instant now = Instant.now();
        alert.setStatus(FraudStatus.CONFIRMED.name());
        alert.setAccountBlocked(true);
        alert.setReviewedBy(CurrentUser.userId());
        alert.setReviewedAt(now);
        alert.setReviewNote(note);
        alert.setUpdatedAt(now);
        alert.appendTimeline(TimelineStep.at(FraudTimelineKey.ACCOUNT_BLOCKED.name(),
                FraudTimelineKey.ACCOUNT_BLOCKED.label(),
                "Account " + alert.getSenderAccountId() + " blocked by " + alert.getReviewedBy()
                        + ": " + reason, now));
        FraudAlert saved = alertRepository.save(alert);

        decisionPublisher.publishAccountBlocked(new AccountBlockedEvent(
                saved.getSenderAccountId(),
                saved.getSenderUserId(),
                saved.getSenderAccountNumber(),
                AccountStatus.ACTIVE.name(),
                AccountStatus.BLOCKED.name(),
                reason,
                saved.getReviewedBy(),
                null,
                saved.getCurrency(),
                now));
        audit(saved, FraudAction.ACCOUNT_BLOCKED, note,
                "Sender account " + saved.getSenderAccountId() + " blocked from fraud alert "
                        + saved.getId() + ".");
        return FraudAlertMapper.toResponse(saved);
    }

    private void blockSenderAccount(FraudAlert alert, String reason) {
        try {
            accountServiceClient.updateStatus(alert.getSenderAccountId(),
                    AccountStatusUpdateRequest.blocked(reason));
        } catch (BusinessException failure) {
            throw failure;
        } catch (Exception failure) {
            if (isAlreadyBlocked(failure)) {
                log.info("Sender account {} was already blocked, continuing with the review",
                        alert.getSenderAccountId());
                return;
            }
            log.error("Account service refused to block account {}", alert.getSenderAccountId(), failure);
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "The account service is unavailable, so account " + alert.getSenderAccountId()
                            + " could not be blocked. The alert was not confirmed - please retry.");
        }
    }

    private boolean isAlreadyBlocked(Exception failure) {
        if (failure instanceof FeignException.Conflict) {
            return true;
        }
        return failure.getMessage() != null && failure.getMessage().contains("409");
    }

    private FraudAlert require(String id) {
        return alertRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.FRAUD_ALERT_NOT_FOUND,
                        "Fraud alert", id));
    }

    private FraudAlert requireActionable(String id) {
        FraudAlert alert = require(id);
        if (!ACTIONABLE.contains(alert.getStatus())) {
            throw new BusinessException(ErrorCode.ALERT_ALREADY_REVIEWED,
                    "Fraud alert " + id + " is already " + alert.getStatus()
                            + " and cannot be reviewed again.");
        }
        return alert;
    }

    private void applyFilters(Query query, FraudAlertFilters filters) {
        List<Criteria> criteria = new ArrayList<>();
        if (filters.status() != null && !filters.status().isBlank()) {
            criteria.add(Criteria.where("status").is(filters.status().toUpperCase()));
        }
        if (filters.riskLevel() != null && !filters.riskLevel().isBlank()) {
            criteria.add(Criteria.where("riskLevel").is(filters.riskLevel().toUpperCase()));
        }
        if (filters.from() != null) {
            criteria.add(Criteria.where("createdAt").gte(filters.from()));
        }
        if (filters.to() != null) {
            criteria.add(Criteria.where("createdAt").lte(filters.to()));
        }
        if (filters.search() != null && !filters.search().isBlank()) {
            String regex = Pattern.quote(filters.search().trim());
            criteria.add(new Criteria().orOperator(
                    Criteria.where("reference").regex(regex, "i"),
                    Criteria.where("transactionId").regex(regex, "i"),
                    Criteria.where("senderAccountNumber").regex(regex, "i"),
                    Criteria.where("senderAccountId").regex(regex, "i")));
        }
        if (!criteria.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteria));
        }
    }

    private void audit(FraudAlert alert, FraudAction action, String note, String message) {
        AuditRecordEvent event = FraudDecisionPublisher.auditFor(alert, action.name(), note,
                CurrentUser.userId(), MDC.get(CorrelationId.MDC_KEY), message);
        decisionPublisher.publishAuditRecord(event);
    }

    /** Rebuilds the event shape the transaction service expects from the stored assessment. */
    private TransactionEvent asTransactionEvent(FraudAlert alert) {
        return new TransactionEvent(
                alert.getTransactionId(),
                alert.getReference(),
                alert.getSenderAccountId(),
                alert.getReceiverAccountId(),
                alert.getSenderAccountNumber(),
                null,
                alert.getSenderUserId(),
                null,
                alert.getAmount(),
                alert.getCurrency(),
                "Funds released by fraud review " + alert.getId(),
                "TRANSFER",
                RiskLevel.HIGH.name().equals(alert.getRiskLevel()) ? "FLAGGED" : "PENDING",
                alert.getRiskScore(),
                alert.getRiskLevel(),
                alert.getReasons() == null ? List.of() : alert.getReasons(),
                null,
                alert.getSenderUserId(),
                null,
                null);
    }
}
