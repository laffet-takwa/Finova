package com.finova.fraud.config;

import com.finova.fraud.service.FraudRuleEngine;
import com.finova.fraud.service.rules.BurstVelocityRule;
import com.finova.fraud.service.rules.FraudRule;
import com.finova.fraud.service.rules.LargeAmountRule;
import com.finova.fraud.service.rules.NewAccountRecipientRule;
import com.finova.fraud.service.rules.RoundAmountProbeRule;
import com.finova.fraud.service.rules.UnusualPatternRule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class FraudRuleConfig {

    /**
     * Evaluation order matters. The corroboration-only structuring probe is registered last so it
     * can observe that another rule already fired.
     */
    @Bean
    public List<FraudRule> fraudRules(FraudProperties properties) {
        return List.of(
                new LargeAmountRule(properties.getLargeAmount()),
                new BurstVelocityRule(properties.getVelocity()),
                new UnusualPatternRule(properties.getUnusualPattern()),
                new NewAccountRecipientRule(properties.getNewRecipient()),
                new RoundAmountProbeRule(properties.getRoundAmount(), properties.getLargeAmount()));
    }

    @Bean
    public FraudRuleEngine fraudRuleEngine(List<FraudRule> rules, FraudProperties properties) {
        return new FraudRuleEngine(rules, properties.getCorroborationBonus());
    }
}
