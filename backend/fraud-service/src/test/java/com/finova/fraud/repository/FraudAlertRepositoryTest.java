package com.finova.fraud.repository;

import com.finova.common.domain.FraudStatus;
import com.finova.common.domain.RiskLevel;
import com.finova.fraud.domain.FraudAlert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataMongoTest
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("FraudAlertRepository")
class FraudAlertRepositoryTest {

    @Container
    private static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
    }

    @Autowired
    private FraudAlertRepository repository;
    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    void shouldEnforceTheUniqueIndexOnTransactionId() {
        repository.insert(alert("txn-unique"));
        FraudAlert duplicate = alert("txn-unique");

        assertThatThrownBy(() -> repository.insert(duplicate))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void shouldCreateTheCompoundStatusRiskCreatedAtIndex() {
        mongoTemplate.indexOps(FraudAlert.class).ensureIndex(new Index()
                .on("status", Sort.Direction.ASC)
                .on("riskLevel", Sort.Direction.ASC)
                .on("createdAt", Sort.Direction.DESC)
                .named("idx_alert_status_risk"));

        List<IndexInfo> indexes = mongoTemplate.indexOps(FraudAlert.class).getIndexInfo();
        assertThat(indexes).anyMatch(info -> "idx_alert_status_risk".equals(info.getName()));
    }

    @Test
    void shouldFindOnlyUnresolvedAlertsThroughTheStatusIndex() {
        Instant now = Instant.now();
        repository.insert(alertWith("txn-open", FraudStatus.OPEN, RiskLevel.HIGH, now));
        repository.insert(alertWith("txn-review", FraudStatus.UNDER_REVIEW, RiskLevel.MEDIUM, now));
        repository.insert(alertWith("txn-safe", FraudStatus.SAFE, RiskLevel.LOW, now));

        List<FraudAlert> unresolved = repository
                .findByStatusInOrderByCreatedAtDesc(List.of(FraudStatus.OPEN.name(),
                        FraudStatus.UNDER_REVIEW.name()));

        assertThat(unresolved).extracting(FraudAlert::getStatus)
                .containsExactlyInAnyOrder(FraudStatus.OPEN.name(), FraudStatus.UNDER_REVIEW.name());
        assertThat(unresolved).hasSize(2);
    }

    @Test
    void shouldCountByRiskLevelWithinTheUnresolvedBacklog() {
        Instant now = Instant.now();
        repository.insert(alertWith("txn-high", FraudStatus.OPEN, RiskLevel.HIGH, now));
        repository.insert(alertWith("txn-high-resolved", FraudStatus.SAFE, RiskLevel.HIGH, now));
        repository.insert(alertWith("txn-low", FraudStatus.OPEN, RiskLevel.LOW, now));

        long high = repository.countByRiskLevelAndStatusIn(RiskLevel.HIGH.name(),
                List.of(FraudStatus.OPEN.name(), FraudStatus.UNDER_REVIEW.name()));

        assertThat(high).isEqualTo(1);
    }

    @Test
    void shouldLookUpAnAlertByTransactionId() {
        repository.insert(alert("txn-lookup"));

        assertThat(repository.findByTransactionId("txn-lookup")).isPresent();
        assertThat(repository.findByTransactionId("txn-absent")).isEmpty();
    }

    private FraudAlert alert(String transactionId) {
        return alertWith(transactionId, FraudStatus.OPEN, RiskLevel.HIGH, Instant.now());
    }

    private FraudAlert alertWith(String transactionId, FraudStatus status, RiskLevel level, Instant createdAt) {
        return FraudAlert.builder()
                .id(transactionId + "-" + UUID.randomUUID())
                .transactionId(transactionId)
                .reference("TX-20260615-00042")
                .senderAccountId("acc-sender-1")
                .receiverAccountId("acc-receiver-1")
                .senderAccountNumber("TN12345678")
                .senderUserId("usr-sender-1")
                .amount(new BigDecimal("15000.000"))
                .currency("TND")
                .riskScore(88)
                .riskLevel(level.name())
                .reasons(List.of("Large transaction amount"))
                .triggeredRules(List.of("LARGE_AMOUNT"))
                .status(status.name())
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .decisionPublished(true)
                .timeline(List.of())
                .build();
    }
}