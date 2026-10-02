package com.finova.common.domain;

/**
 * Risk bands derived from the fraud rule engine score.
 * <ul>
 *   <li>0 - 30: LOW</li>
 *   <li>31 - 70: MEDIUM</li>
 *   <li>71 - 100: HIGH</li>
 * </ul>
 */
public enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH;

    public static RiskLevel fromScore(int score) {
        if (score >= 71) {
            return HIGH;
        }
        if (score >= 31) {
            return MEDIUM;
        }
        return LOW;
    }
}
