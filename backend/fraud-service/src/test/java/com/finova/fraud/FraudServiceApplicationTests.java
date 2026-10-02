package com.finova.fraud;

import com.finova.fraud.service.FraudRuleEngine;
import com.finova.fraud.service.rules.FraudRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.cloud.service-registry.eureka.enabled=false",
        "eureka.client.enabled=false",
        "management.health.mongo.enabled=false",
        "spring.data.mongodb.uri=mongodb://localhost:27017/finova_fraud_context_probe",
        "finova.jwt.secret=finova-fraud-context-test-secret-key-32-bytes"
})
@DisplayName("Application context")
class FraudServiceApplicationTests {

    @Autowired
    private ApplicationContext context;

    @Test
    void shouldWiringTheTypedKafkaTemplateTheRulesAndTheAdminOnlyFilterChain() {
        assertThat(context.getBean(KafkaTemplate.class)).isNotNull();
        assertThat(context.getBean("domainEventTemplate")).isInstanceOf(KafkaTemplate.class);

        FraudRuleEngine engine = context.getBean(FraudRuleEngine.class);
        List<FraudRule> rules = engine.rules();
        assertThat(rules).extracting(FraudRule::id)
                .containsExactly("LARGE_AMOUNT", "BURST_VELOCITY", "UNUSUAL_PATTERN",
                        "NEW_ACCOUNT_RECIPIENT", "ROUND_AMOUNT_PROBE");
        assertThat(engine.corroborationBonus()).isEqualTo(8);
        assertThat(context.containsBean("fraudFilterChain")).isTrue();
    }
}