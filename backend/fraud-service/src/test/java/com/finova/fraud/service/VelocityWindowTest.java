package com.finova.fraud.service;

import com.finova.common.event.TransactionEvent;
import com.finova.fraud.config.FraudProperties;
import com.finova.fraud.config.MongoConverterConfig;
import com.finova.fraud.domain.FraudAlert;
import com.finova.fraud.domain.VelocityWindow;
import com.finova.fraud.event.FraudDecisionPublisher;
import com.finova.fraud.service.rules.BurstVelocityRule;
import com.finova.fraud.service.rules.FraudRule;
import com.finova.fraud.service.rules.LargeAmountRule;
import com.finova.fraud.service.rules.NewAccountRecipientRule;
import com.finova.fraud.service.rules.RoundAmountProbeRule;
import com.finova.fraud.service.rules.UnusualPatternRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest(properties = "spring.data.mongodb.auto-index-creation=true")
@Testcontainers(disabledWithoutDocker = true)
@Import({MongoConverterConfig.class, VelocityWindowTest.Wiring.class, FraudScoringService.class})
@DisplayName("VelocityWindow atomic bookkeeping")
class VelocityWindowTest {

    @Container
    private static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
    }

    @MockBean
    private FraudDecisionPublisher decisionPublisher;

    @Autowired
    private MongoTemplate mongoTemplate;
    @Autowired
    private FraudScoringService scoringService;

    @BeforeEach
    void clear() {
        mongoTemplate.remove(new Query(), VelocityWindow.class);
        mongoTemplate.remove(new Query(), FraudAlert.class);
    }

    @Test
    void shouldResetTheWindowWhenWindowStartFallsOutsideTheWindow() {
        String accountId = "acc-reset";
        Instant stale = Instant.now().minus(30, ChronoUnit.MINUTES);
        mongoTemplate.insert(VelocityWindow.builder()
                .id(VelocityWindow.idFor(accountId, "TND"))
                .senderAccountId(accountId)
                .currency("TND")
                .windowStart(stale)
                .eventCount(9)
                .totalAmount(new BigDecimal("9000.000"))
                .recentAmounts(List.of(new BigDecimal("500.000"), new BigDecimal("500.000")))
                .transfersSeen(40)
                .firstSeenAt(stale)
                .updatedAt(stale)
                .build());

        FraudAlert alert = scoringService.evaluate(transfer(accountId, new BigDecimal("600.000")));

        VelocityWindow refreshed = read(accountId);
        assertThat(refreshed.getEventCount()).isEqualTo(1);
        assertThat(refreshed.getTotalAmount()).isEqualByComparingTo("600.000");
        assertThat(refreshed.getWindowStart()).isAfter(Instant.now().minusSeconds(60));
        assertThat(refreshed.getTransfersSeen()).isEqualTo(41);
        assertThat(refreshed.getRecentAmounts()).hasSize(3);
        assertThat(alert.getTriggeredRules()).doesNotContain(BurstVelocityRule.ID);
    }

    @Test
    void shouldNotResetTheWindowWhileItIsStillFresh() {
        String accountId = "acc-fresh";
        Instant fresh = Instant.now().minusSeconds(10);
        mongoTemplate.insert(VelocityWindow.builder()
                .id(VelocityWindow.idFor(accountId, "TND"))
                .senderAccountId(accountId)
                .currency("TND")
                .windowStart(fresh)
                .eventCount(3)
                .totalAmount(new BigDecimal("900.000"))
                .recentAmounts(List.of(new BigDecimal("300.000")))
                .transfersSeen(3)
                .firstSeenAt(fresh)
                .updatedAt(fresh)
                .build());

        scoringService.evaluate(transfer(accountId, new BigDecimal("300.000")));

        VelocityWindow refreshed = read(accountId);
        assertThat(refreshed.getEventCount()).isEqualTo(4);
        assertThat(refreshed.getTotalAmount()).isEqualByComparingTo("1200.000");
        assertThat(refreshed.getTransfersSeen()).isEqualTo(4);
    }

    @Test
    void shouldKeepEveryCountUnderConcurrentIncrements() throws Exception {
        String accountId = "acc-concurrent";
        int writers = 12;
        int perWriter = 5;
        ExecutorService pool = Executors.newFixedThreadPool(writers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(writers);
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < writers; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int j = 0; j < perWriter; j++) {
                        scoringService.evaluate(transfer(accountId, new BigDecimal("100.000")));
                    }
                } catch (Exception failure) {
                    failures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(120, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        VelocityWindow window = read(accountId);
        assertThat(failures.get()).isZero();
        assertThat(window.getTransfersSeen()).isEqualTo((long) writers * perWriter);
        assertThat(window.getTotalAmount()).isEqualByComparingTo("6000.000");
        assertThat(window.getRecentAmounts()).hasSize(20);
        assertThat(mongoTemplate.count(new Query(), FraudAlert.class)).isEqualTo((long) writers * perWriter);
    }

    @Test
    void shouldKeepOnlyTheConfiguredNumberOfBaselineAmounts() {
        String accountId = "acc-baseline";
        for (int i = 0; i < 24; i++) {
            scoringService.evaluate(transfer(accountId, new BigDecimal("100.000")));
        }

        VelocityWindow window = read(accountId);

        assertThat(window.getRecentAmounts()).hasSize(20);
        assertThat(window.getTransfersSeen()).isEqualTo(24);
    }

    @Test
    void shouldAssessEachTransactionOnlyOnceWhenTheEventIsRedelivered() {
        String accountId = "acc-dedupe";
        TransactionEvent transfer = transfer(accountId, new BigDecimal("15000.000"));

        scoringService.evaluate(transfer);
        FraudAlert second = scoringService.evaluate(transfer);

        assertThat(second.getRiskScore()).isEqualTo(88);
        assertThat(second.isDecisionPublished()).isTrue();
        assertThat(mongoTemplate.count(new Query(), FraudAlert.class)).isEqualTo(1);
    }

    private VelocityWindow read(String accountId) {
        return mongoTemplate.findById(VelocityWindow.idFor(accountId, "TND"), VelocityWindow.class);
    }

    private TransactionEvent transfer(String accountId, BigDecimal amount) {
        return new TransactionEvent(
                "txn-" + UUID.randomUUID(), "TX-20260615-00042", accountId, "acc-receiver-1",
                "TN12345678", "TN87654321", "usr-sender-1", "usr-receiver-1", amount, "TND", "Rent",
                "TRANSFER", "PENDING", null, null, List.of(), null, "usr-sender-1", "10.0.0.1", null);
    }

    @Configuration
    @EnableConfigurationProperties(FraudProperties.class)
    static class Wiring {

        @Bean
        public FraudRuleEngine fraudRuleEngine(FraudProperties properties) {
            List<FraudRule> rules = List.of(
                    new LargeAmountRule(properties.getLargeAmount()),
                    new BurstVelocityRule(properties.getVelocity()),
                    new UnusualPatternRule(properties.getUnusualPattern()),
                    new NewAccountRecipientRule(properties.getNewRecipient()),
                    new RoundAmountProbeRule(properties.getRoundAmount(), properties.getLargeAmount()));
            return new FraudRuleEngine(rules, properties.getCorroborationBonus());
        }
    }
}