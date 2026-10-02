package com.finova.fraud.service.rules;

import com.finova.fraud.config.FraudProperties;

/**
 * Rule 4 - NEW_ACCOUNT_RECIPIENT.
 * <p>
 * The sender has not paid this beneficiary inside the configured lookback window. A brand new payee
 * is not fraud on its own, but it is the cheapest precursor signal there is: account takeover
 * attempts overwhelmingly start by redirecting a first, previously unknown, beneficiary.
 * <p>
 * The lookback state is the sender to receiver pair, tracked in {@code recipient_history}.
 */
public class NewAccountRecipientRule implements FraudRule {

    public static final String ID = "NEW_ACCOUNT_RECIPIENT";

    private final FraudProperties.NewRecipient settings;

    public NewAccountRecipientRule(FraudProperties.NewRecipient settings) {
        this.settings = settings;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "Flags a beneficiary this sender has never paid before inside the lookback window.";
    }

    @Override
    public RuleOutcome evaluate(FraudContext context) {
        String receiver = context.receiverAccountId();
        if (receiver == null || receiver.isBlank() || context.recipientSeenRecently()) {
            return RuleOutcome.notTriggered();
        }
        return RuleOutcome.triggered(settings.getScore(),
                "Beneficiary " + receiver + " has not been paid by this account in the last "
                        + settings.getLookbackDays() + " days.");
    }
}
