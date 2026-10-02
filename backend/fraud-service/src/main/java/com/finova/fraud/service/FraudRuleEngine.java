package com.finova.fraud.service;

import com.finova.common.domain.RiskLevel;
import com.finova.fraud.service.rules.FraudContext;
import com.finova.fraud.service.rules.FraudRule;
import com.finova.fraud.service.rules.RuleOutcome;

import java.util.ArrayList;
import java.util.List;

/**
 * The rule engine. Pure: no Spring, no Mongo, no clock of its own, no I/O.
 * <p>
 * Scoring is <em>weighted sum plus corroboration</em>. The weights are the domain opinion carried
 * by each rule; the corroboration bonus is what the sum alone cannot express, namely that two
 * independent signals agreeing is stronger evidence than their arithmetic total. A single rule pays
 * its weight, every additional rule that also fires adds {@code corroborationBonus} on top, and the
 * total is clamped to the 0-100 band before {@link RiskLevel#fromScore(int)} bands it.
 * <p>
 * Rules are evaluated in list order and each one sees whether an earlier rule fired, which is how
 * the corroboration-only rule stays silent when it has nothing to corroborate.
 */
public class FraudRuleEngine {

    private final List<FraudRule> rules;
    private final int corroborationBonus;

    public FraudRuleEngine(List<FraudRule> rules, int corroborationBonus) {
        this.rules = List.copyOf(rules);
        this.corroborationBonus = corroborationBonus;
    }

    public List<FraudRule> rules() {
        return rules;
    }

    public int corroborationBonus() {
        return corroborationBonus;
    }

    public FraudAssessment assess(FraudContext context) {
        List<RuleOutcome> outcomes = new ArrayList<>(rules.size());
        boolean anyTriggered = false;
        for (FraudRule rule : rules) {
            RuleOutcome outcome = rule.evaluate(context.withAnotherRuleTriggered(anyTriggered));
            outcomes.add(outcome);
            anyTriggered = anyTriggered || outcome.triggered();
        }

        int baseScore = outcomes.stream().mapToInt(RuleOutcome::score).sum();
        long triggeredCount = outcomes.stream().filter(RuleOutcome::triggered).count();
        int bonus = (int) Math.max(0L, triggeredCount - 1) * corroborationBonus;
        int score = clamp(baseScore + bonus);

        List<String> reasons = outcomes.stream()
                .filter(RuleOutcome::triggered)
                .map(RuleOutcome::reason)
                .toList();
        List<String> triggeredRules = new ArrayList<>();
        for (int i = 0; i < outcomes.size(); i++) {
            if (outcomes.get(i).triggered()) {
                triggeredRules.add(rules.get(i).id());
            }
        }

        return new FraudAssessment(score, RiskLevel.fromScore(score), baseScore, bonus,
                reasons, triggeredRules, List.copyOf(outcomes));
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
