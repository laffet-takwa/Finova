package com.finova.fraud.service.rules;

import com.finova.common.event.TransactionEvent;
import com.finova.fraud.config.FraudProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Rule 4 - NEW_ACCOUNT_RECIPIENT")
class NewAccountRecipientRuleTest {

    private final FraudProperties properties = new FraudProperties();
    private final NewAccountRecipientRule rule = new NewAccountRecipientRule(properties.getNewRecipient());

    @Test
    void shouldTriggerWhenTheBeneficiaryIsUnknown() {
        FraudContext context = FraudContexts.context(
                FraudContexts.transaction(new BigDecimal("400.000"), "TND"),
                FraudContexts.steadyWindow(new BigDecimal("400.000")), false, FraudContexts.NOW);

        RuleOutcome outcome = rule.evaluate(context);

        assertThat(outcome.triggered()).isTrue();
        assertThat(outcome.score()).isEqualTo(10);
        assertThat(outcome.reason()).contains("has not been paid by this account in the last 7 days");
    }

    @Test
    void shouldNotTriggerWhenTheBeneficiaryWasSeenRecently() {
        FraudContext context = FraudContexts.context(
                FraudContexts.transaction(new BigDecimal("400.000"), "TND"),
                FraudContexts.steadyWindow(new BigDecimal("400.000")), true, FraudContexts.NOW);

        assertThat(rule.evaluate(context).triggered()).isFalse();
    }

    @Test
    void shouldNotTriggerWithoutAKnownReceiver() {
        TransactionEvent withoutReceiver = new TransactionEvent(
                "txn-1", "TX-20260615-00042", FraudContexts.SENDER_ACCOUNT, null,
                "TN12345678", null, "usr-sender-1", null, new BigDecimal("400.000"), "TND", "Rent",
                "TRANSFER", "PENDING", null, null, List.of(), null, "usr-sender-1", "10.0.0.1", null);
        FraudContext context = FraudContexts.context(
                withoutReceiver,
                FraudContexts.steadyWindow(new BigDecimal("400.000")), false, FraudContexts.NOW);

        assertThat(rule.evaluate(context).triggered()).isFalse();
    }
}