package com.finova.fraud.service;

import com.finova.common.domain.RiskLevel;
import com.finova.fraud.service.rules.RuleOutcome;

import java.util.List;

/**
 * Outcome of one full engine run.
 *
 * @param score          clamped 0-100 risk score
 * @param level          risk band derived from the score
 * @param baseScore      sum of the rule weights before corroboration and clamping
 * @param bonus          corroboration points added for each rule beyond the first
 * @param reasons        human readable justifications, in rule evaluation order
 * @param triggeredRules rule identifiers, in rule evaluation order
 * @param outcomes       every rule outcome, triggered or not
 */
public record FraudAssessment(
        int score,
        RiskLevel level,
        int baseScore,
        int bonus,
        List<String> reasons,
        List<String> triggeredRules,
        List<RuleOutcome> outcomes
) {

    public boolean isHighRisk() {
        return level == RiskLevel.HIGH;
    }
}
