package com.finova.fraud.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The assessment of one transfer.
 * <p>
 * Every evaluated transaction produces a document so the score history is auditable: only
 * {@code riskLevel == HIGH} leaves the alert in {@code status = OPEN}. LOW and MEDIUM assessments
 * are stored with {@code status = SAFE} and a closed timeline.
 */
@Document(collection = "fraud_alerts")
@CompoundIndexes({
        @CompoundIndex(name = "idx_alert_status_risk",
                def = "{'status':1,'riskLevel':1,'createdAt':-1}"),
        @CompoundIndex(name = "idx_alert_sender_time", def = "{'senderAccountId':1,'createdAt':-1}")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudAlert {

    @Id
    private String id;

    @Indexed(unique = true)
    private String transactionId;

    private String reference;
    private String senderAccountId;
    private String receiverAccountId;
    private String senderAccountNumber;
    private String senderUserId;
    private BigDecimal amount;
    private String currency;
    private int riskScore;
    private String riskLevel;
    private List<String> reasons;
    private List<String> triggeredRules;
    private String status;

    @Indexed
    private Instant createdAt;

    private Instant updatedAt;
    private Instant reviewedAt;
    private String reviewedBy;
    private String reviewNote;
    private List<TimelineStep> timeline;

    /**
     * Set once the fraud decision has been handed to the outbox. Guarantees a single decision
     * event per transaction even if {@code transaction.created} is redelivered.
     */
    private boolean decisionPublished;

    /** Terminal outcome reported by the transaction service: COMPLETED or FAILED. */
    private String resolvedByTransaction;

    /** True once the sender account has been blocked through the account service. */
    private boolean accountBlocked;

    public void appendTimeline(TimelineStep step) {
        if (timeline == null) {
            timeline = new ArrayList<>();
        }
        timeline.add(step);
    }

    public List<TimelineStep> timelineOrEmpty() {
        return timeline == null ? List.of() : timeline;
    }
}
