package com.finova.fraud.service.rules;

import com.finova.fraud.config.FraudProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Rule 5 - ROUND_AMOUNT_PROBE")
class RoundAmountProbeRuleTest {

    private final FraudProperties properties = new FraudProperties();
    private final RoundAmountProbeRule rule =
            new RoundAmountProbeRule(properties.getRoundAmount(), properties.getLargeAmount());

    @Test
    void shouldTriggerOnARoundAmountBelowTheThresholdWhenCorroborated() {
        FraudContext context = FraudContexts.context(
                FraudContexts.transaction(new BigDecimal("2000.000"), "EUR"),
                FraudContexts.steadyWindow(new BigDecimal("400.000"))).withAnotherRuleTriggered(true);

        RuleOutcome outcome = rule.evaluate(context);

        assertThat(outcome.triggered()).isTrue();
        assertThat(outcome.score()).isEqualTo(10);
        assertThat(outcome.reason()).contains("Round amount of 2000.000 EUR");
    }

    @Test
    void shouldNeverFireAlone() {
        FraudContext context = FraudContexts.context(
                FraudContexts.transaction(new BigDecimal("2000.000"), "EUR"),
                FraudContexts.steadyWindow(new BigDecimal("400.000"))).withAnotherRuleTriggered(false);

        RuleOutcome outcome = rule.evaluate(context);

        assertThat(outcome.triggered()).isFalse();
        assertThat(outcome.score()).isZero();
    }

    @Test
    void shouldNotTriggerOnANonRoundAmount() {
        FraudContext context = FraudContexts.context(
                FraudContexts.transaction(new BigDecimal("2000.750"), "EUR"),
                FraudContexts.steadyWindow(new BigDecimal("400.000"))).withAnotherRuleTriggered(true);

        assertThat(rule.evaluate(context).triggered()).isFalse();
    }

    @Test
    void shouldNotTriggerBelowTheMinimumRoundFigure() {
        FraudContext context = FraudContexts.context(
                FraudContexts.transaction(new BigDecimal("900.000"), "EUR"),
                FraudContexts.steadyWindow(new BigDecimal("400.000"))).withAnotherRuleTriggered(true);

        assertThat(rule.evaluate(context).triggered()).isFalse();
    }

    @Test
    void shouldNotTriggerOnARoundAmountAboveTheThresholdAlreadyCaughtByRuleOne() {
        FraudContext context = FraudContexts.context(
                FraudContexts.transaction(new BigDecimal("15000.000"), "TND"),
                FraudContexts.steadyWindow(new BigDecimal("310.000"))).withAnotherRuleTriggered(true);

        RuleOutcome outcome = rule.evaluate(context);

        assertThat(outcome.triggered()).isFalse();
        assertThat(outcome.reason()).isNull();
    }
}