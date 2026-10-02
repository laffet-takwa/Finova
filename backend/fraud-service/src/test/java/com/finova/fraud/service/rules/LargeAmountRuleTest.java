package com.finova.fraud.service.rules;

import com.finova.fraud.config.FraudProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Rule 1 - LARGE_AMOUNT")
class LargeAmountRuleTest {

    private final FraudProperties properties = new FraudProperties();
    private final LargeAmountRule rule = new LargeAmountRule(properties.getLargeAmount());

    @Test
    void shouldTriggerWhenAmountIsAboveTheTndThreshold() {
        FraudContext context = FraudContexts.context(
                FraudContexts.transaction(new BigDecimal("12450.750"), "TND"),
                FraudContexts.steadyWindow(new BigDecimal("310.000")));

        RuleOutcome outcome = rule.evaluate(context);

        assertThat(outcome.triggered()).isTrue();
        assertThat(outcome.score()).isEqualTo(55);
        assertThat(outcome.reason())
                .isEqualTo("Large transaction amount of 12450.750 TND exceeds the 10000.000 TND review threshold.");
    }

    @Test
    void shouldNotTriggerWhenAmountIsExactlyAtTheThreshold() {
        FraudContext context = FraudContexts.context(
                FraudContexts.transaction(new BigDecimal("10000.000"), "TND"),
                FraudContexts.steadyWindow(new BigDecimal("310.000")));

        RuleOutcome outcome = rule.evaluate(context);

        assertThat(outcome.triggered()).isFalse();
        assertThat(outcome.score()).isZero();
    }

    @Test
    void shouldApplyTheCurrencyAwareThreshold() {
        FraudContext belowEurThreshold = FraudContexts.context(
                FraudContexts.transaction(new BigDecimal("2000.000"), "EUR"),
                FraudContexts.steadyWindow(new BigDecimal("310.000")));
        FraudContext aboveEurThreshold = FraudContexts.context(
                FraudContexts.transaction(new BigDecimal("3000.000"), "EUR"),
                FraudContexts.steadyWindow(new BigDecimal("310.000")));

        assertThat(rule.evaluate(belowEurThreshold).triggered()).isFalse();
        assertThat(rule.evaluate(aboveEurThreshold).triggered()).isTrue();
        assertThat(rule.evaluate(aboveEurThreshold).reason()).contains("2500.000 EUR");
    }

    @Test
    void shouldFallBackToTheTndThresholdForAnUnknownCurrency() {
        FraudContext context = FraudContexts.context(
                FraudContexts.transaction(new BigDecimal("12000.000"), "GBP"),
                FraudContexts.steadyWindow(new BigDecimal("310.000")));

        RuleOutcome outcome = rule.evaluate(context);

        assertThat(outcome.triggered()).isTrue();
        assertThat(outcome.reason()).contains("12000.000 GBP").contains("10000.000 GBP");
    }

    @Test
    void shouldExposeItsIdentifierAndDescription() {
        assertThat(rule.id()).isEqualTo("LARGE_AMOUNT");
        assertThat(rule.description()).isNotBlank();
    }
}