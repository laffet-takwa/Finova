package com.finova.notification.service;

import com.finova.common.domain.NotificationType;
import com.finova.common.event.AccountBlockedEvent;
import com.finova.common.event.DomainEvent;
import com.finova.common.event.Topics;
import com.finova.common.event.TransactionEvent;
import com.finova.common.support.Money;
import com.finova.notification.domain.NotificationCategory;
import com.finova.notification.domain.NotificationSeverity;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns a platform event into the inbox entries the recipients will actually read.
 * <p>
 * Deliberately free of Spring, of repositories and of the security context: it is a
 * pure function of (topic, event), which is what makes the wording and the
 * recipient rules directly unit-testable.
 * <p>
 * Two rules matter more than the copy itself:
 * <ul>
 *   <li>Only terminal facts notify. {@code transaction.approved} is the fraud
 *       decision to proceed, not the money having moved, and this factory returns
 *       nothing for it. Any topic the inventory does not name as terminal is
 *       ignored rather than guessed at.</li>
 *   <li>The receiver is notified only for a settled transfer. A failed or held
 *       transfer is the sender's business to hear about; telling the receiver about
 *       money that did not arrive (or arrived and was then held) would be noise at
 *       best and a lie at worst.</li>
 * </ul>
 * Account numbers are reduced to their last four digits before they reach any
 * string a user or a log will see.
 */
public final class NotificationFactory {

    /** Dedup key suffix of the entry addressed to the initiator of the transfer. */
    public static final String ROLE_SENDER = "SENDER";
    /** Dedup key suffix of the entry addressed to the beneficiary. */
    public static final String ROLE_RECEIVER = "RECEIVER";
    /** Dedup key suffix of an entry that belongs to the account holder only. */
    public static final String ROLE_OWNER = "OWNER";

    private static final String MASK = "••••";

    /**
     * Builds the inbox entries for a {@code transaction.*} event.
     *
     * @param topic   the Kafka topic the event was read from; it, not the payload
     *                status, decides which wording applies
     * @param envelope the event envelope
     * @return zero, one or two drafts in recipient order; empty for anything that
     *         is not a terminal outcome
     */
    public List<NotificationDraft> fromTransaction(String topic, DomainEvent<TransactionEvent> envelope) {
        if (envelope == null) {
            return List.of();
        }
        TransactionEvent event = envelope.payload();
        if (event == null || isBlank(event.senderUserId())) {
            return List.of();
        }
        String correlationId = envelope.correlationId();
        String sourceService = envelope.sourceService();
        String amountPhrase = amountPhrase(event.amount(), event.currency());
        String transferSubject = amountPhrase.isEmpty()
                ? "Your transfer"
                : "Your transfer of " + amountPhrase;
        String referenceSentence = isBlank(event.reference())
                ? ""
                : " Reference " + event.reference().strip() + ".";
        BigDecimal amount = scaled(event.amount());
        String currency = blankToNull(event.currency());

        List<NotificationDraft> drafts = new ArrayList<>(2);
        switch (topic == null ? "" : topic) {
            case Topics.TRANSACTION_COMPLETED -> {
                drafts.add(new NotificationDraft(
                        event.senderUserId(),
                        NotificationType.TRANSFER_COMPLETED,
                        NotificationCategory.TRANSACTIONS,
                        NotificationSeverity.SUCCESS,
                        "Transfer completed",
                        transferSubject + " to account " + maskAccount(event.receiverAccountNumber())
                                + " was successful.",
                        event.transactionId(),
                        event.reference(),
                        amount,
                        currency,
                        dedupeKey(envelope.eventId(), ROLE_SENDER),
                        correlationId,
                        sourceService));

                if (hasReceiver(event)) {
                    drafts.add(new NotificationDraft(
                            event.receiverUserId(),
                            NotificationType.TRANSFER_COMPLETED,
                            NotificationCategory.TRANSACTIONS,
                            NotificationSeverity.SUCCESS,
                            "Money received",
                            "You received" + (amountPhrase.isEmpty() ? " money" : " " + amountPhrase)
                                    + " from account " + maskAccount(event.senderAccountNumber())
                                    + "." + referenceSentence,
                            event.transactionId(),
                            event.reference(),
                            amount,
                            currency,
                            dedupeKey(envelope.eventId(), ROLE_RECEIVER),
                            correlationId,
                            sourceService));
                }
            }
            case Topics.TRANSACTION_FAILED -> drafts.add(new NotificationDraft(
                    event.senderUserId(),
                    NotificationType.TRANSFER_FAILED,
                    NotificationCategory.TRANSACTIONS,
                    NotificationSeverity.DANGER,
                    "Transfer failed",
                    transferSubject + " could not be completed." + referenceSentence,
                    event.transactionId(),
                    event.reference(),
                    amount,
                    currency,
                    dedupeKey(envelope.eventId(), ROLE_SENDER),
                    correlationId,
                    sourceService));

            case Topics.TRANSACTION_FLAGGED -> drafts.add(new NotificationDraft(
                    event.senderUserId(),
                    NotificationType.TRANSFER_FLAGGED,
                    NotificationCategory.SECURITY,
                    NotificationSeverity.WARNING,
                    "Transfer held for review",
                    transferSubject + " is being reviewed by our security team." + referenceSentence,
                    event.transactionId(),
                    event.reference(),
                    amount,
                    currency,
                    dedupeKey(envelope.eventId(), ROLE_SENDER),
                    correlationId,
                    sourceService));

            default -> {
                // transaction.approved (the fraud decision), transaction.created and
                // anything unknown: no user-facing fact exists yet, so nothing is said.
                return List.of();
            }
        }
        return List.copyOf(drafts);
    }

    /** Builds the single inbox entry produced by {@code account.blocked}. */
    public List<NotificationDraft> fromAccountBlocked(DomainEvent<AccountBlockedEvent> envelope) {
        if (envelope == null) {
            return List.of();
        }
        AccountBlockedEvent event = envelope.payload();
        if (event == null || isBlank(event.userId())) {
            return List.of();
        }
        return List.of(new NotificationDraft(
                event.userId(),
                NotificationType.ACCOUNT_BLOCKED,
                NotificationCategory.SECURITY,
                NotificationSeverity.DANGER,
                "Account blocked",
                "Account " + maskAccount(event.accountNumber())
                        + " has been blocked. Contact support for more information.",
                null,
                null,
                scaled(event.balance()),
                blankToNull(event.currency()),
                dedupeKey(envelope.eventId(), ROLE_OWNER),
                envelope.correlationId(),
                envelope.sourceService()));
    }

    /**
     * A settled transfer can legitimately have no beneficiary on the payload (a
     * closed account, a legacy record). The sender is still notified.
     */
    private static boolean hasReceiver(TransactionEvent event) {
        return !isBlank(event.receiverUserId()) && !event.receiverUserId().equals(event.senderUserId());
    }

    /**
     * The dedupe key is the source event id qualified by the recipient role. A
     * settled transfer produces two entries for one event, so the bare event id
     * would collide on the partial unique index; qualifying it keeps the pair
     * deterministic, so a redelivery still maps to the same keys and is rejected.
     */
    private static String dedupeKey(String eventId, String role) {
        return (isBlank(eventId) ? "unknown" : eventId) + ":" + role;
    }

    private static String maskAccount(String accountNumber) {
        if (isBlank(accountNumber)) {
            return MASK;
        }
        String digits = accountNumber.strip().replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return MASK;
        }
        return MASK + " " + digits.substring(Math.max(0, digits.length() - 4));
    }

    /** Rendered amount with its currency, or an empty string when there is no amount. */
    private static String amountPhrase(BigDecimal amount, String currency) {
        if (amount == null) {
            return "";
        }
        String value = new DecimalFormat("#,##0.000",
                DecimalFormatSymbols.getInstance(Locale.US)).format(Money.scale(amount));
        return isBlank(currency) ? value : value + " " + currency.strip();
    }

    private static BigDecimal scaled(BigDecimal amount) {
        return amount == null ? null : Money.scale(amount);
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.strip();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}