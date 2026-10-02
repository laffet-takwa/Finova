package com.finova.transaction.service;

import java.util.List;

/**
 * The fraud service verdict carried by a settlement decision: the transaction to
 * act on, the risk score and level, and the rule hits that produced them.
 */
public record SettlementDecision(
        String transactionId,
        Integer riskScore,
        String riskLevel,
        List<String> reasons
) {

    public SettlementDecision {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
}