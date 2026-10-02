package com.finova.notification.config;

import com.finova.common.domain.NotificationType;
import com.finova.notification.domain.Notification;
import com.finova.notification.domain.NotificationCategory;
import com.finova.notification.domain.NotificationPreference;
import com.finova.notification.domain.NotificationSeverity;
import com.finova.notification.repository.NotificationPreferenceRepository;
import com.finova.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Realistic demo inbox for the {@code dev} profile.
 *
 * <h2>The user ids below are NOT real user ids</h2>
 * The user-service generates a random {@code UUID} for every registration, so a
 * seeded row can never point at a real user. These ids are stable synthetic
 * placeholders, chosen so the demo data is reproducible:
 * {@code demo-takwa}, {@code demo-ines}, {@code demo-yassine}, {@code demo-salma},
 * {@code demo-sami} and {@code demo-admin}.
 * <p>
 * Anything that proves ownership — the {@code user_id} predicate on every query, the
 * {@code WHERE id = ? AND user_id = ?} scope on every mutation — therefore fails
 * closed for these rows when called with a real token, which is exactly the
 * behaviour that matters. A real user's inbox is never affected by this class.
 * <p>
 * Seeded rows deliberately carry a {@code NULL source_event_id}: they were not
 * produced by a Kafka consumer, so they must not consume the dedupe namespace
 * (which is also why the unique index on that column is partial).
 */
@Component
@Profile("dev")
public class DataSeeder implements ApplicationRunner {

    public static final String DEMO_TAKWA = "demo-takwa";
    public static final String DEMO_INES = "demo-ines";
    public static final String DEMO_YASSINE = "demo-yassine";
    public static final String DEMO_SALMA = "demo-salma";
    public static final String DEMO_SAMI = "demo-sami";
    public static final String DEMO_ADMIN = "demo-admin";

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final DateTimeFormatter REFERENCE_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final List<String> DEMO_USERS = List.of(
            DEMO_TAKWA, DEMO_INES, DEMO_YASSINE, DEMO_SALMA, DEMO_SAMI, DEMO_ADMIN);

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;

    public DataSeeder(NotificationRepository notificationRepository,
                      NotificationPreferenceRepository preferenceRepository) {
        this.notificationRepository = notificationRepository;
        this.preferenceRepository = preferenceRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (notificationRepository.countByUserId(DEMO_TAKWA) > 0) {
            log.info("Demo inbox already present, seeder skipped.");
            return;
        }

        notificationRepository.saveAll(buildInbox(DEMO_TAKWA, 52, 6, personalTemplates(), 20));
        notificationRepository.saveAll(buildInbox(DEMO_INES, 15, 2, secondaryTemplates("Ines"), 17));
        notificationRepository.saveAll(buildInbox(DEMO_YASSINE, 15, 3, secondaryTemplates("Yassine"), 14));
        notificationRepository.saveAll(buildInbox(DEMO_SALMA, 15, 2, secondaryTemplates("Salma"), 12));
        notificationRepository.saveAll(buildInbox(DEMO_SAMI, 15, 1, secondaryTemplates("Sami"), 9));
        notificationRepository.saveAll(buildInbox(DEMO_ADMIN, 5, 1, adminTemplates(), 6));

        seedPreferences();

        announceDemoData();
    }

    private void seedPreferences() {
        Instant now = Instant.now();
        for (String userId : DEMO_USERS) {
            if (preferenceRepository.findById(userId).isEmpty()) {
                preferenceRepository.save(NotificationPreference.defaults(userId, now));
            }
        }
        NotificationPreference takwa = preferenceRepository.findById(DEMO_TAKWA).orElseThrow();
        takwa.setMarketingEmails(false);
        takwa.setPushEnabled(true);
        preferenceRepository.save(takwa);
    }

    /**
     * Spreads {@code count} entries over {@code windowDays}, oldest at the bottom of
     * the window, and keeps the newest {@code unreadCount} of them unread so the
     * badge and the unread list have something real to show.
     */
    private List<Notification> buildInbox(String userId, int count, int unreadCount,
                                          List<Template> templates, int windowDays) {
        List<Notification> inbox = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            Template template = templates.get(index % templates.size());
            int dayOffset = count <= 1 ? 0
                    : (int) Math.round((double) windowDays * (count - 1 - index) / (count - 1));
            Instant createdAt = Instant.now()
                    .minus(dayOffset, ChronoUnit.DAYS)
                    .minus(6 + (index % 13), ChronoUnit.HOURS)
                    .minus((index * 7) % 53, ChronoUnit.MINUTES)
                    .truncatedTo(ChronoUnit.SECONDS);
            boolean read = index >= unreadCount;

            Notification notification = new Notification();
            notification.setId(UUID.randomUUID().toString());
            notification.setUserId(userId);
            notification.setType(template.type());
            notification.setCategory(template.category());
            notification.setSeverity(template.severity());
            notification.setTitle(template.title());
            notification.setMessage(template.text().replace("{amount}", format(template.amount())));
            notification.setAmount(template.amount());
            notification.setCurrency(template.amount() == null ? null : "TND");
            notification.setTransactionId(isTransfer(template.type())
                    ? UUID.randomUUID().toString()
                    : null);
            notification.setReference(isTransfer(template.type())
                    ? reference(createdAt, index)
                    : null);
            notification.setRead(read);
            notification.setReadAt(read ? createdAt.plus(2 + (index % 40), ChronoUnit.HOURS) : null);
            notification.setCreatedAt(createdAt);
            notification.setCorrelationId(UUID.randomUUID().toString());
            notification.setSourceService(template.sourceService());
            notification.setSourceEventId(null);
            inbox.add(notification);
        }
        return inbox;
    }

    private static boolean isTransfer(NotificationType type) {
        return type == NotificationType.TRANSFER_COMPLETED
                || type == NotificationType.TRANSFER_FAILED
                || type == NotificationType.TRANSFER_FLAGGED;
    }

    private static String reference(Instant createdAt, int index) {
        String date = REFERENCE_DATE.format(createdAt.atOffset(ZoneOffset.UTC));
        return "TX-" + date + "-" + String.format(Locale.US, "%05d", 10_000 + index);
    }

    private static String format(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        return String.format(Locale.US, "%,.3f", amount);
    }

    private void announceDemoData() {
        StringBuilder banner = new StringBuilder();
        banner.append(System.lineSeparator());
        banner.append("==================================================================").append(System.lineSeparator());
        banner.append("=== FINOVA NOTIFICATION DEMO DATA ===").append(System.lineSeparator());
        banner.append("==================================================================").append(System.lineSeparator());
        banner.append("Seeded ").append(notificationRepository.count()).append(" notifications for ").append(DEMO_USERS.size())
                .append(" synthetic users.").append(System.lineSeparator());
        banner.append("THE USER IDS BELOW ARE NOT REAL USER IDS.").append(System.lineSeparator());
        banner.append("The user-service assigns a random UUID to every registration, so these").append(System.lineSeparator());
        banner.append("stable placeholders can never match a real account. They exist only so").append(System.lineSeparator());
        banner.append("the dev inbox is reproducible. They are NOT credentials and NOT login ids.").append(System.lineSeparator());
        for (String userId : DEMO_USERS) {
            banner.append("  - ").append(userId)
                    .append("  unread=").append(notificationRepository.countByUserIdAndReadFalse(userId))
                    .append(" total=").append(notificationRepository.countByUserId(userId))
                    .append(System.lineSeparator());
        }
        banner.append("Rows carry source_event_id = NULL on purpose: they are not Kafka").append(System.lineSeparator());
        banner.append("deliveries and must not occupy the consumer dedupe namespace.").append(System.lineSeparator());
        banner.append("==================================================================");
        log.info(banner.toString());
    }

    private record Template(NotificationType type, NotificationCategory category, NotificationSeverity severity,
                            String title, String text, BigDecimal amount, String sourceService) {
    }

    private static Template transfer(String title, String text, String amount, String sourceService) {
        return new Template(NotificationType.TRANSFER_COMPLETED, NotificationCategory.TRANSACTIONS,
                NotificationSeverity.SUCCESS, title, text, amount(amount), sourceService);
    }

    private static BigDecimal amount(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private List<Template> personalTemplates() {
        List<Template> templates = new ArrayList<>();
        templates.add(transfer("Monthly salary received",
                "You received {amount} TND from account •••• 1180. Reference TX-20260901-00014.",
                "4250.000", "transaction-service"));
        templates.add(transfer("Electricity bill payment — STEG",
                "Your payment of {amount} TND to STEG was successful.",
                "186.400", "transaction-service"));
        templates.add(transfer("Grocery shopping — Carrefour",
                "Your payment of {amount} TND at Carrefour Sfax was successful.",
                "274.850", "transaction-service"));
        templates.add(transfer("Transfer to Yassine Trabelsi",
                "Your transfer of {amount} TND to account •••• 4321 was successful.",
                "350.000", "transaction-service"));
        templates.add(transfer("Card payment — Netflix",
                "Your payment of {amount} TND to Netflix was successful.",
                "39.990", "transaction-service"));
        templates.add(transfer("Fuel — Shell",
                "Your payment of {amount} TND at Shell Les Berges du Lac was successful.",
                "92.500", "transaction-service"));
        templates.add(new Template(NotificationType.SECURITY_ALERT, NotificationCategory.SECURITY,
                NotificationSeverity.WARNING, "New device sign-in detected",
                "A sign-in from a device we had not seen before was recorded on 12 September at 21:04 "
                        + "from Sfax, Tunisia. If this was not you, change your password now.",
                null, "user-service"));
        templates.add(new Template(NotificationType.TRANSFER_FLAGGED, NotificationCategory.SECURITY,
                NotificationSeverity.WARNING, "Transfer held for review",
                "Your transfer of {amount} TND to account •••• 9007 is being reviewed by our security "
                        + "team. Reference TX-20260918-00002.",
                amount("4800.000"), "fraud-service"));
        templates.add(new Template(NotificationType.TRANSFER_FAILED, NotificationCategory.TRANSACTIONS,
                NotificationSeverity.DANGER, "Transfer failed",
                "Your transfer of {amount} TND to account •••• 6620 could not be completed. "
                        + "Reference TX-20260920-00003.",
                amount("1250.000"), "transaction-service"));
        templates.add(new Template(NotificationType.ACCOUNT_BLOCKED, NotificationCategory.SECURITY,
                NotificationSeverity.DANGER, "Account blocked",
                "Account •••• 8901 has been blocked. Contact support for more information.",
                null, "account-service"));
        templates.add(transfer("Rent payment",
                "Your payment of {amount} TND to Agence Immobilière El Menzah was successful.",
                "1100.000", "transaction-service"));
        templates.add(new Template(NotificationType.SECURITY_ALERT, NotificationCategory.SECURITY,
                NotificationSeverity.INFO, "Password changed",
                "Your Finova password was changed. If you did not make this change, contact support "
                        + "immediately.",
                null, "user-service"));
        templates.add(transfer("Refund — Electroplanet",
                "You received {amount} TND as a refund from Electroplanet. Reference TX-20260925-00007.",
                "620.000", "transaction-service"));
        templates.add(new Template(NotificationType.TRANSFER_FAILED, NotificationCategory.TRANSACTIONS,
                NotificationSeverity.DANGER, "Transfer failed",
                "Your transfer of {amount} TND to account •••• 1178 could not be completed: the "
                        + "available balance was too low.",
                amount("75.000"), "transaction-service"));
        templates.add(new Template(NotificationType.SECURITY_ALERT, NotificationCategory.SECURITY,
                NotificationSeverity.WARNING, "Unusual activity detected",
                "Three transfers were attempted to a new beneficiary within five minutes. "
                        + "Review your recent activity if you do not recognise them.",
                null, "fraud-service"));
        return templates;
    }

    private List<Template> secondaryTemplates(String owner) {
        List<Template> templates = new ArrayList<>();
        templates.add(transfer("Monthly salary received",
                "You received {amount} TND from account •••• 7745. Reference TX-20260901-00031.",
                "2950.000", "transaction-service"));
        templates.add(transfer("Water bill — SONEDE",
                "Your payment of {amount} TND to SONEDE was successful.",
                "48.300", "transaction-service"));
        templates.add(transfer("Supermarket — Monoprix",
                "Your payment of {amount} TND at Monoprix was successful.",
                "163.750", "transaction-service"));
        templates.add(transfer("School fees — Lycée Pilote",
                "Your payment of {amount} TND to Lycée Pilote Sfax was successful.",
                "850.000", "transaction-service"));
        templates.add(transfer("Card payment — Spotify",
                "Your payment of {amount} TND to Spotify was successful.",
                "24.900", "transaction-service"));
        templates.add(new Template(NotificationType.SECURITY_ALERT, NotificationCategory.SECURITY,
                NotificationSeverity.WARNING, "New device sign-in detected",
                owner + " signed in from a device we had not seen before, on 14 September at 07:52 "
                        + "from Tunis, Tunisia. If this was not you, change your password now.",
                null, "user-service"));
        templates.add(new Template(NotificationType.TRANSFER_FLAGGED, NotificationCategory.SECURITY,
                NotificationSeverity.WARNING, "Transfer held for review",
                "Your transfer of {amount} TND to account •••• 5510 is being reviewed by our security "
                        + "team. Reference TX-20260917-00009.",
                amount("3100.000"), "fraud-service"));
        templates.add(new Template(NotificationType.TRANSFER_FAILED, NotificationCategory.TRANSACTIONS,
                NotificationSeverity.DANGER, "Transfer failed",
                "Your transfer of {amount} TND to account •••• 3390 could not be completed. "
                        + "Reference TX-20260922-00005.",
                amount("540.000"), "transaction-service"));
        return templates;
    }

    private List<Template> adminTemplates() {
        List<Template> templates = new ArrayList<>();
        templates.add(new Template(NotificationType.SECURITY_ALERT, NotificationCategory.SYSTEM,
                NotificationSeverity.WARNING, "Fraud rule set reloaded",
                "The fraud rule set was reloaded and 14 rules are now active.",
                null, "fraud-service"));
        templates.add(transfer("Treasury sweep — daily settlement",
                "The daily settlement sweep of {amount} TND completed with no unmatched legs.",
                "184500.000", "transaction-service"));
        templates.add(new Template(NotificationType.SECURITY_ALERT, NotificationCategory.SYSTEM,
                NotificationSeverity.INFO, "New analyst joined",
                "A new fraud analyst was added to the review queue.",
                null, "user-service"));
        templates.add(new Template(NotificationType.ACCOUNT_BLOCKED, NotificationCategory.SECURITY,
                NotificationSeverity.DANGER, "Account blocked",
                "Account •••• 3341 has been blocked. Contact support for more information.",
                null, "account-service"));
        templates.add(new Template(NotificationType.TRANSFER_FLAGGED, NotificationCategory.SECURITY,
                NotificationSeverity.WARNING, "Transfer held for review",
                "A transfer of {amount} TND was held automatically for manual review.",
                amount("92000.000"), "fraud-service"));
        return templates;
    }
}