package com.finova.fraud.service;

import com.finova.common.event.TransactionEvent;
import com.finova.fraud.config.FraudProperties;
import com.finova.fraud.domain.FraudAlert;
import com.finova.fraud.domain.VelocityWindow;
import com.finova.fraud.event.FraudDecisionPublisher;
import com.finova.fraud.repository.FraudAlertRepository;
import com.finova.fraud.repository.RecipientHistoryRepository;
import com.finova.fraud.service.rules.BurstVelocityRule;
import com.finova.fraud.service.rules.FraudRule;
import com.finova.fraud.service.rules.LargeAmountRule;
import com.finova.fraud.service.rules.NewAccountRecipientRule;
import com.finova.fraud.service.rules.RoundAmountProbeRule;
import com.finova.fraud.service.rules.UnusualPatternRule;
import org.bson.Document;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the shape of the velocity window update. The live behaviour is covered by
 * {@code VelocityWindowTest} against a real MongoDB; this test runs everywhere and asserts the two
 * properties the concurrency guarantee rests on, namely that the reset is conditional and that
 * neither step ever upserts.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Velocity window update is two conditional, non-upserting steps")
class VelocityWindowAtomicityTest {

    @Mock
    private FraudAlertRepository alertRepository;
    @Mock
    private RecipientHistoryRepository recipientRepository;
    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private FraudDecisionPublisher decisionPublisher;

    @Test
    void shouldResetOnlyWhenTheWindowHasExpiredAndNeverUpsert() {
        FraudScoringService service = service();
        when(alertRepository.findByTransactionId("txn-1")).thenReturn(Optional.empty());
        when(recipientRepository.findById("acc-sender-1->acc-receiver-1")).thenReturn(Optional.empty());
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(VelocityWindow.class))).thenReturn(null);
        when(mongoTemplate.insert(any(VelocityWindow.class))).thenAnswer(call -> call.getArgument(0));

        service.evaluate(transfer());

        ArgumentCaptor<Query> queries = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<FindAndModifyOptions> options = ArgumentCaptor.forClass(FindAndModifyOptions.class);
        verify(mongoTemplate, times(2)).findAndModify(queries.capture(), any(Update.class),
                options.capture(), eq(VelocityWindow.class));

        Query reset = queries.getAllValues().get(0);
        assertThat(reset.getQueryObject()).containsKey("windowStart");
        assertThat(reset.getQueryObject().get("_id")).isEqualTo("acc-sender-1:TND");
        Query increment = queries.getAllValues().get(1);
        assertThat(increment.getQueryObject()).containsOnlyKeys("_id");

        options.getAllValues().forEach(value -> assertThat(value.isUpsert()).isFalse());
    }

    @Test
    void shouldCapTheBaselineByPushingNewestFirst() {
        FraudScoringService service = service();
        when(alertRepository.findByTransactionId("txn-1")).thenReturn(Optional.empty());
        when(recipientRepository.findById("acc-sender-1->acc-receiver-1")).thenReturn(Optional.empty());
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(VelocityWindow.class))).thenReturn(null);
        when(mongoTemplate.insert(any(VelocityWindow.class))).thenAnswer(call -> call.getArgument(0));

        service.evaluate(transfer());

        ArgumentCaptor<Update> updates = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, times(2)).findAndModify(any(Query.class), updates.capture(),
                any(FindAndModifyOptions.class), eq(VelocityWindow.class));

        Document push = updates.getAllValues().get(1).getUpdateObject().get("$push", Document.class)
                .get("recentAmounts", Document.class);
        assertThat(push.getInteger("$position")).isZero();
        assertThat(push.getInteger("$slice")).isEqualTo(20);

        Document increment = updates.getAllValues().get(1).getUpdateObject().get("$inc", Document.class);
        assertThat(increment).containsKeys("eventCount", "totalAmount", "transfersSeen");
    }

    private FraudScoringService service() {
        FraudProperties properties = new FraudProperties();
        List<FraudRule> rules = List.of(
                new LargeAmountRule(properties.getLargeAmount()),
                new BurstVelocityRule(properties.getVelocity()),
                new UnusualPatternRule(properties.getUnusualPattern()),
                new NewAccountRecipientRule(properties.getNewRecipient()),
                new RoundAmountProbeRule(properties.getRoundAmount(), properties.getLargeAmount()));
        return new FraudScoringService(new FraudRuleEngine(rules, properties.getCorroborationBonus()),
                alertRepository, recipientRepository, mongoTemplate, decisionPublisher, properties);
    }

    private TransactionEvent transfer() {
        return new TransactionEvent(
                "txn-1", "TX-20260615-00042", "acc-sender-1", "acc-receiver-1", "TN12345678", "TN87654321",
                "usr-sender-1", "usr-receiver-1", new BigDecimal("15000.000"), "TND", "Rent", "TRANSFER",
                "PENDING", null, null, List.of(), null, "usr-sender-1", "10.0.0.1", null);
    }
}