package com.finova.fraud.service.rules;

import com.finova.fraud.config.FraudProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Rule 2 - BURST_VELOCITY")
class BurstVelocityRuleTest {

    private final FraudProperties properties = new FraudProperties();
    private final BurstVelocityRule rule = new BurstVelocityRule(properties.getVelocity());

    @Test
    void shouldTriggerWithTheMediumScoreAtSixEvents() {
        FraudContext context = FraudContexts.context(
                FraudContexts.transaction(new BigDecimal("400.000"), "TND"),
                FraudContexts.burstWindow(6, new BigDecimal("380.000")));

        RuleOutcome outcome = rule.evaluate(context);

        assertThat(outcome.triggered()).isTrue();
        assertThat(outcome.score()).isEqualTo(30);
        assertThat(outcome.reason()).isEqualTo("6 transactions from this account within the last 60 seconds.");
    }

    @Test
    void shouldNotTriggerAtExactlyFiveEvents() {
        FraudContext context = FraudContexts.context(
                FraudContexts.transaction(new BigDecimal("400.000"), "TND"),
                FraudContexts.burstWindow(5, new BigDecimal("380.000")));

        RuleOutcome outcome = rule.evaluate(context);

        assertThat(outcome.triggered()).isFalse();
        assertThat(outcome.score()).isZero();
    }

    @Test
    void shouldUseTheHighScoreAboveTenEvents() {
        FraudContext context = FraudContexts.context(
                FraudContexts.transaction(new BigDecimal("400.000"), "TND"),
                FraudContexts.burstWindow(11, new BigDecimal("380.000")));

        RuleOutcome outcome = rule.evaluate(context);

        assertThat(outcome.triggered()).isTrue();
        assertThat(outcome.score()).isEqualTo(45);
    }

    @Test
    void shouldNotTriggerWhenTheWindowIsEmpty() {
        FraudContext context = FraudContexts.context(
                FraudContexts.transaction(new BigDecimal("400.000"), "TND"),
                null);

        assertThat(rule.evaluate(context).triggered()).isFalse();
    }
}