package com.finova.fraud.service;

import com.finova.common.domain.FraudStatus;
import com.finova.common.domain.RiskLevel;
import com.finova.common.event.TransactionEvent;
import com.finova.fraud.config.FraudProperties;
import com.finova.fraud.domain.FraudAlert;
import com.finova.fraud.domain.RecipientHistory;
import com.finova.fraud.domain.VelocityWindow;
import com.finova.fraud.event.FraudDecisionPublisher;
import com.finova.fraud.repository.FraudAlertRepository;
import com.finova.fraud.repository.RecipientHistoryRepository;
import com.finova.fraud.service.rules.BurstVelocityRule;
import com.finova.fraud.service.rules.FraudContexts;
import com.finova.fraud.service.rules.FraudRule;
import com.finova.fraud.service.rules.LargeAmountRule;
import com.finova.fraud.service.rules.NewAccountRecipientRule;
import com.finova.fraud.service.rules.RoundAmountProbeRule;
import com.finova.fraud.service.rules.UnusualPatternRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FraudScoringService")
class FraudScoringServiceTest {

    @Mock
    private FraudAlertRepository alertRepository;
    @Mock
    private RecipientHistoryRepository recipientRepository;
    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private FraudDecisionPublisher decisionPublisher;

    private FraudScoringService scoringService;

    @BeforeEach
    void setUp() {
        FraudProperties properties = new FraudProperties();
        List<FraudRule> rules = List.of(
                new LargeAmountRule(properties.getLargeAmount()),
                new BurstVelocityRule(properties.getVelocity()),
                new UnusualPatternRule(properties.getUnusualPattern()),
                new NewAccountRecipientRule(properties.getNewRecipient()),
                new RoundAmountProbeRule(properties.getRoundAmount(), properties.getLargeAmount()));
        scoringService = new FraudScoringService(
                new FraudRuleEngine(rules, properties.getCorroborationBonus()),
                alertRepository, recipientRepository, mongoTemplate, decisionPublisher, properties);
    }

    @Test
    void shouldPersistAnAlertCarryingTheComputedScoreAndLevel() {
        givenVelocityWindow(FraudContexts.burstWindow(1, new BigDecimal("3571.428"), new BigDecimal("3500.000")));
        givenNoExistingAlert();
        givenKnownBeneficiary();

        FraudAlert alert = scoringService.evaluate(transfer(new BigDecimal("15000.000")));

        ArgumentCaptor<FraudAlert> saved = ArgumentCaptor.forClass(FraudAlert.class);
        verify(alertRepository, times(2)).save(saved.capture());
        FraudAlert persisted = saved.getAllValues().get(0);
        assertThat(alert.getRiskScore()).isEqualTo(88);
        assertThat(alert.getRiskLevel()).isEqualTo(RiskLevel.HIGH.name());
        assertThat(persisted.getStatus()).isEqualTo(FraudStatus.OPEN.name());
        assertThat(persisted.getTransactionId()).isEqualTo("txn-1");
        assertThat(persisted.getTriggeredRules())
                .containsExactly(LargeAmountRule.ID, UnusualPatternRule.ID);
        assertThat(alert.getTimeline()).hasSize(5);
        assertThat(alert.getTimeline().get(4).getKey()).isEqualTo("ALERT_CREATED");
    }

    @Test
    void shouldCloseTheAssessmentImmediatelyForALowRiskTransfer() {
        givenVelocityWindow(FraudContexts.steadyWindow(new BigDecimal("400.000"), new BigDecimal("420.000")));
        givenNoExistingAlert();
        givenKnownBeneficiary();

        FraudAlert alert = scoringService.evaluate(transfer(new BigDecimal("400.000")));

        assertThat(alert.getRiskLevel()).isEqualTo(RiskLevel.LOW.name());
        assertThat(alert.getStatus()).isEqualTo(FraudStatus.SAFE.name());
        assertThat(alert.getTimeline()).hasSize(4);
    }

    @Test
    void shouldNotCreateASecondAlertWhenTheSameTransactionIsReEvaluated() {
        FraudAlert already = FraudAlert.builder()
                .id("alert-1")
                .transactionId("txn-1")
                .status(FraudStatus.OPEN.name())
                .riskScore(88)
                .riskLevel(RiskLevel.HIGH.name())
                .decisionPublished(true)
                .build();
        when(alertRepository.findByTransactionId("txn-1")).thenReturn(Optional.of(already));

        FraudAlert alert = scoringService.evaluate(transfer(new BigDecimal("15000.000")));

        assertThat(alert.getId()).isEqualTo("alert-1");
        assertThat(alert.getRiskScore()).isEqualTo(88);
        verify(alertRepository, never()).save(any(FraudAlert.class));
        verify(decisionPublisher, never()).publishDecision(any(), any());
    }

    @Test
    void shouldPublishTheDecisionExactlyOnceWhenTheConsumerReceivesTheEventTwice() {
        givenVelocityWindow(FraudContexts.burstWindow(1, new BigDecimal("3571.428"), new BigDecimal("3500.000")));
        givenNoExistingAlert();
        givenKnownBeneficiary();

        scoringService.evaluate(transfer(new BigDecimal("15000.000")));
        FraudAlert firstPass = FraudAlert.builder()
                .id("alert-1")
                .transactionId("txn-1")
                .status(FraudStatus.OPEN.name())
                .riskScore(88)
                .riskLevel(RiskLevel.HIGH.name())
                .decisionPublished(true)
                .build();
        when(alertRepository.findByTransactionId("txn-1")).thenReturn(Optional.of(firstPass));

        scoringService.evaluate(transfer(new BigDecimal("15000.000")));

        verify(decisionPublisher, times(1)).publishDecision(any(FraudAlert.class), any(TransactionEvent.class));
    }

    @Test
    void shouldPublishAndFlagTheAssessmentAsSafeForAMediumRiskTransfer() {
        givenVelocityWindow(FraudContexts.steadyWindow(new BigDecimal("250.000"), new BigDecimal("260.000")));
        givenNoExistingAlert();
        givenUnknownBeneficiary();

        scoringService.evaluate(transfer(new BigDecimal("1500.000")));

        ArgumentCaptor<FraudAlert> saved = ArgumentCaptor.forClass(FraudAlert.class);
        verify(alertRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(1).isDecisionPublished()).isTrue();
        assertThat(saved.getAllValues().get(1).getStatus()).isEqualTo(FraudStatus.SAFE.name());
        assertThat(saved.getAllValues().get(1).getRiskLevel()).isEqualTo(RiskLevel.MEDIUM.name());
        verify(decisionPublisher, times(1)).publishDecision(any(FraudAlert.class), any(TransactionEvent.class));
    }

    private void givenNoExistingAlert() {
        when(alertRepository.findByTransactionId("txn-1")).thenReturn(Optional.empty());
    }

    private void givenKnownBeneficiary() {
        RecipientHistory history = RecipientHistory.builder()
                .id(RecipientHistory.idFor("acc-sender-1", "acc-receiver-1"))
                .senderAccountId("acc-sender-1")
                .receiverAccountId("acc-receiver-1")
                .lastTransferredAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .transferCount(4)
                .build();
        when(recipientRepository.findById(history.getId())).thenReturn(Optional.of(history));
        when(recipientRepository.save(history)).thenReturn(history);
    }

    private void givenUnknownBeneficiary() {
        when(recipientRepository.findById(RecipientHistory.idFor("acc-sender-1", "acc-receiver-1")))
                .thenReturn(Optional.empty());
    }

    private void givenVelocityWindow(VelocityWindow window) {
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(VelocityWindow.class))).thenReturn(window);
    }

    private TransactionEvent transfer(BigDecimal amount) {
        return new TransactionEvent(
                "txn-1", "TX-20260615-00042", "acc-sender-1", "acc-receiver-1", "TN12345678", "TN87654321",
                "usr-sender-1", "usr-receiver-1", amount, "TND", "Rent", "TRANSFER",
                "PENDING", null, null, List.of(), null, "usr-sender-1", "10.0.0.1", null);
    }
}