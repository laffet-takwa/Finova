package com.finova.fraud.service.rules;

import com.finova.fraud.config.FraudProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Rule 3 - UNUSUAL_PATTERN")
class UnusualPatternRuleTest {

    private final FraudProperties properties = new FraudProperties();
    private final UnusualPatternRule rule = new UnusualPatternRule(properties.getUnusualPattern());

    @Test
    void shouldTriggerOnAFourTimesJumpAgainstTheAverage() {
        FraudContext context = FraudContexts.context(
                FraudContexts.transaction(new BigDecimal("1240.000"), "TND"),
                FraudContexts.steadyWindow(new BigDecimal("310.000"), new BigDecimal("310.000")));

        RuleOutcome outcome = rule.evaluate(context);

        assertThat(outcome.triggered()).isTrue();
        assertThat(outcome.score()).isEqualTo(25);
        assertThat(outcome.reason())
                .isEqualTo("Amount is 4.0x the sender's average of 310.000 TND over the last 2 transactions.");
    }

    @Test
    void shouldNotTriggerOnAStableAmount() {
        FraudContext context = FraudContexts.context(
                FraudContexts.transaction(new BigDecimal("320.000"), "TND"),
                FraudContexts.steadyWindow(new BigDecimal("310.000"), new BigDecimal("305.000")));

        RuleOutcome outcome = rule.evaluate(context);

        assertThat(outcome.triggered()).isFalse();
        assertThat(outcome.score()).isZero();
    }

    @Test
    void shouldTriggerWhenMoreThanTwiceTheLargestPreviousAmount() {
        FraudContext context = FraudContexts.context(
                FraudContexts.transaction(new BigDecimal("2100.000"), "TND"),
                FraudContexts.steadyWindow(new BigDecimal("1000.000"), new BigDecimal("500.000")));

        RuleOutcome outcome = rule.evaluate(context);

        assertThat(outcome.triggered()).isTrue();
        assertThat(outcome.reason()).contains("largest previous transfer of 1000.000 TND");
    }

    @Test
    void shouldTriggerOnTheFirstEverTransfer() {
        FraudContext context = FraudContexts.context(
                FraudContexts.transaction(new BigDecimal("50.000"), "TND"),
                FraudContexts.steadyWindow());

        RuleOutcome outcome = rule.evaluate(context);

        assertThat(outcome.triggered()).isTrue();
        assertThat(outcome.reason()).isEqualTo("First ever transfer from this account, no baseline exists yet.");
    }

    @Test
    void shouldTriggerDuringTheUnusualHourBand() {
        FraudContext context = FraudContexts.context(
                FraudContexts.transaction(new BigDecimal("310.000"), "TND"),
                FraudContexts.steadyWindow(new BigDecimal("310.000"), new BigDecimal("300.000")),
                true,
                Instant.parse("2026-06-15T03:20:00Z"));

        RuleOutcome outcome = rule.evaluate(context);

        assertThat(outcome.triggered()).isTrue();
        assertThat(outcome.reason()).contains("outside normal banking hours");
    }

    @Test
    void shouldNotTriggerOutsideTheUnusualHourBand() {
        FraudContext context = FraudContexts.context(
                FraudContexts.transaction(new BigDecimal("310.000"), "TND"),
                FraudContexts.steadyWindow(new BigDecimal("310.000"), new BigDecimal("300.000")),
                true,
                Instant.parse("2026-06-15T09:20:00Z"));

        assertThat(rule.evaluate(context).triggered()).isFalse();
    }
}