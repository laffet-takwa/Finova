package com.finova.fraud.service;

import com.finova.common.domain.RiskLevel;
import com.finova.fraud.config.FraudProperties;
import com.finova.fraud.service.rules.BurstVelocityRule;
import com.finova.fraud.service.rules.FraudContext;
import com.finova.fraud.service.rules.FraudContexts;
import com.finova.fraud.service.rules.FraudRule;
import com.finova.fraud.service.rules.LargeAmountRule;
import com.finova.fraud.service.rules.NewAccountRecipientRule;
import com.finova.fraud.service.rules.RoundAmountProbeRule;
import com.finova.fraud.service.rules.RuleOutcome;
import com.finova.fraud.service.rules.UnusualPatternRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Fraud rule engine - pure, no Spring and no Mongo")
class FraudRuleEngineTest {

    private final FraudProperties properties = new FraudProperties();
    private final FraudRuleEngine engine = new FraudRuleEngine(rules(), properties.getCorroborationBonus());

    private List<com.finova.fraud.service.rules.FraudRule> rules() {
        return List.of(
                new LargeAmountRule(properties.getLargeAmount()),
                new BurstVelocityRule(properties.getVelocity()),
                new UnusualPatternRule(properties.getUnusualPattern()),
                new NewAccountRecipientRule(properties.getNewRecipient()),
                new RoundAmountProbeRule(properties.getRoundAmount(), properties.getLargeAmount()));
    }

    @Test
    void shouldScoreLowForASmallNormalTransfer() {
        FraudContext context = FraudContexts.context(
                FraudContexts.transaction(new BigDecimal("250.000"), "TND"),
                FraudContexts.steadyWindow(new BigDecimal("240.000"), new BigDecimal("260.000"),
                        new BigDecimal("255.000")));

        FraudAssessment assessment = engine.assess(context);

        assertThat(assessment.level()).isEqualTo(RiskLevel.LOW);
        assertThat(assessment.score()).isLessThanOrEqualTo(30);
        assertThat(assessment.reasons()).isEmpty();
        assertThat(assessment.triggeredRules()).isEmpty();
    }

    @Test
    void shouldScoreHighForAFifteenThousandTndTransfer() {
        FraudContext context = FraudContexts.context(
                FraudContexts.transaction(new BigDecimal("15000.000"), "TND"),
                FraudContexts.burstWindow(2, new BigDecimal("3571.428"), new BigDecimal("3500.000")));

        FraudAssessment assessment = engine.assess(context);

        assertThat(assessment.level()).isEqualTo(RiskLevel.HIGH);
        assertThat(assessment.score()).isGreaterThanOrEqualTo(71);
        assertThat(assessment.triggeredRules()).contains(LargeAmountRule.ID, UnusualPatternRule.ID);
    }

    @Test
    void shouldScore88ForTheWorkedExampleFromTheClientSpecification() {
        FraudContext context = FraudContexts.context(
                FraudContexts.transaction(new BigDecimal("15000.000"), "TND"),
                FraudContexts.burstWindow(1, new BigDecimal("3571.428"), new BigDecimal("3500.000"),
                        new BigDecimal("3600.000")));

        FraudAssessment assessment = engine.assess(context);

        assertThat(assessment.baseScore()).isEqualTo(80);
        assertThat(assessment.bonus()).isEqualTo(8);
        assertThat(assessment.score()).isEqualTo(88);
        assertThat(assessment.level()).isEqualTo(RiskLevel.HIGH);
        assertThat(assessment.triggeredRules()).containsExactly(LargeAmountRule.ID, UnusualPatternRule.ID);
        assertThat(assessment.reasons()).anyMatch(reason -> reason.startsWith("Large transaction amount"));
        assertThat(assessment.reasons()).anyMatch(reason -> reason.contains("x the sender's average"));
    }

    @Test
    void shouldFireTheBurstRuleAtSixEventsAndNotAtFive() {
        FraudAssessment atFive = engine.assess(burstContext(5));
        FraudAssessment atSix = engine.assess(burstContext(6));

        assertThat(atFive.triggeredRules()).doesNotContain(BurstVelocityRule.ID);
        assertThat(atSix.triggeredRules()).contains(BurstVelocityRule.ID);
        assertThat(atSix.baseScore()).isEqualTo(30);
        assertThat(atSix.bonus()).isZero();
        assertThat(atSix.score()).isEqualTo(30);
    }

    @Test
    void shouldFireTheUnusualPatternRuleOnAFourTimesJump() {
        FraudContext jump = FraudContexts.context(
                FraudContexts.transaction(new BigDecimal("1240.000"), "TND"),
                FraudContexts.steadyWindow(new BigDecimal("310.000"), new BigDecimal("310.000")));

        FraudContext stable = FraudContexts.context(
                FraudContexts.transaction(new BigDecimal("305.000"), "TND"),
                FraudContexts.steadyWindow(new BigDecimal("310.000"), new BigDecimal("310.000")));

        assertThat(engine.assess(jump).triggeredRules()).contains(UnusualPatternRule.ID);
        assertThat(engine.assess(stable).triggeredRules()).doesNotContain(UnusualPatternRule.ID);
    }

    @Test
    void shouldNeverFireTheRoundAmountRuleAlone() {
        FraudAssessment assessment = engine.assess(FraudContexts.context(
                FraudContexts.transaction(new BigDecimal("9000.000"), "TND"),
                FraudContexts.steadyWindow(new BigDecimal("6000.000"), new BigDecimal("6200.000"))));

        assertThat(assessment.triggeredRules()).doesNotContain(RoundAmountProbeRule.ID);
        assertThat(assessment.score()).isZero();
    }

    @Test
    void shouldFireTheRoundAmountRuleWhenItCorroboratesAnotherRule() {
        FraudAssessment assessment = engine.assess(FraudContexts.context(
                FraudContexts.transaction(new BigDecimal("9000.000"), "TND"),
                FraudContexts.steadyWindow(new BigDecimal("600.000"), new BigDecimal("620.000"))));

        assertThat(assessment.triggeredRules()).contains(UnusualPatternRule.ID, RoundAmountProbeRule.ID);
        assertThat(assessment.score()).isEqualTo(43);
    }

    @Test
    void shouldClampTheScoreToOneHundred() {
        FraudAssessment assessment = engine.assess(FraudContexts.context(
                FraudContexts.transaction(new BigDecimal("15000.000"), "TND"),
                FraudContexts.burstWindow(14, new BigDecimal("100.000"))));

        assertThat(assessment.baseScore()).isGreaterThan(100);
        assertThat(assessment.score()).isEqualTo(100);
        assertThat(assessment.level()).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void shouldNeverProduceANegativeScore() {
        FraudProperties negativeBonus = new FraudProperties();
        negativeBonus.setCorroborationBonus(-1000);
        FraudRuleEngine hostile = new FraudRuleEngine(rules(), negativeBonus.getCorroborationBonus());

        FraudAssessment assessment = hostile.assess(FraudContexts.context(
                FraudContexts.transaction(new BigDecimal("15000.000"), "TND"),
                FraudContexts.burstWindow(14, new BigDecimal("100.000"))));

        assertThat(assessment.score()).isZero();
        assertThat(assessment.level()).isEqualTo(RiskLevel.LOW);
    }

    @Test
    void shouldBandTheScoreOnTheCanonicalRiskBoundaries() {
        assertThat(RiskLevel.fromScore(30)).isEqualTo(RiskLevel.LOW);
        assertThat(RiskLevel.fromScore(31)).isEqualTo(RiskLevel.MEDIUM);
        assertThat(RiskLevel.fromScore(70)).isEqualTo(RiskLevel.MEDIUM);
        assertThat(RiskLevel.fromScore(71)).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void shouldApplyTheDocumentedWeightTable() {
        assertThat(properties.getLargeAmount().getScore()).isEqualTo(55);
        assertThat(properties.getVelocity().getMediumScore()).isEqualTo(30);
        assertThat(properties.getVelocity().getHighScore()).isEqualTo(45);
        assertThat(properties.getVelocity().getMaxTransactions()).isEqualTo(5);
        assertThat(properties.getVelocity().getWindowSeconds()).isEqualTo(60);
        assertThat(properties.getUnusualPattern().getScore()).isEqualTo(25);
        assertThat(properties.getUnusualPattern().getAverageMultiplier()).isEqualByComparingTo("3");
        assertThat(properties.getUnusualPattern().getLargestMultiplier()).isEqualByComparingTo("2");
        assertThat(properties.getNewRecipient().getScore()).isEqualTo(10);
        assertThat(properties.getNewRecipient().getLookbackDays()).isEqualTo(7);
        assertThat(properties.getRoundAmount().getScore()).isEqualTo(10);
        assertThat(properties.getRoundAmount().getMinimum()).isEqualByComparingTo("1000");
        assertThat(properties.getCorroborationBonus()).isEqualTo(8);
    }

    @Test
    void shouldExposeEveryOutcomeIncludingTheOnesThatDidNotFire() {
        FraudAssessment assessment = engine.assess(FraudContexts.context(
                FraudContexts.transaction(new BigDecimal("15000.000"), "TND"),
                FraudContexts.burstWindow(1, new BigDecimal("3571.428"), new BigDecimal("3500.000"))));

        List<String> ids = new ArrayList<>();
        engine.rules().forEach(rule -> ids.add(rule.id()));
        assertThat(assessment.outcomes()).hasSameSizeAs(ids);
        assertThat(assessment.outcomes().stream().filter(RuleOutcome::triggered)).hasSize(2);
    }

    private FraudContext burstContext(int eventCount) {
        return FraudContexts.context(
                FraudContexts.transaction(new BigDecimal("400.000"), "TND"),
                FraudContexts.burstWindow(eventCount, new BigDecimal("400.000")));
    }
}