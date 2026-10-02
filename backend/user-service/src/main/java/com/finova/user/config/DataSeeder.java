package com.finova.user.config;

import com.finova.common.audit.AuditAction;
import com.finova.common.domain.Role;
import com.finova.common.domain.UserStatus;
import com.finova.user.domain.AuditLog;
import com.finova.user.domain.User;
import com.finova.user.repository.AuditLogRepository;
import com.finova.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Demo identities and audit history for the {@code dev} profile.
 * <p>
 * The audit rows are the interesting half. On a fresh clone the admin console and
 * its charts are empty, which makes an empty dashboard indistinguishable from a
 * broken one; seeding thirty days of history means the screens are demonstrable
 * immediately, and it is also what gives the audit consumer a backlog to read when
 * Kafka is running.
 * <p>
 * Nothing outside this service is seeded. Accounts, transfers and fraud alerts belong
 * to the account, transaction and fraud services, and duplicating them here would
 * produce two sets of ids for the same customer.
 */
@Component
@Profile("dev")
@ConditionalOnProperty(name = "finova.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder implements ApplicationRunner {

    /** The demo password shared by every seeded identity. */
    public static final String DEMO_PASSWORD = "Finova#2026";

    public static final String DEMO_ADMIN = "admin@finova.dev";
    public static final String DEMO_TAKWA = "takwa@finova.dev";
    public static final String DEMO_INES = "ines.bouzid@finova.dev";
    public static final String DEMO_YASSINE = "yassine.trabelsi@finova.dev";
    public static final String DEMO_SALMA = "salma.gharbi@finova.dev";
    public static final String DEMO_SAMI = "sami.mejboud@finova.dev";

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final int AUDIT_ROWS = 48;
    private static final int AUDIT_WINDOW_DAYS = 30;

    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository,
                      AuditLogRepository auditLogRepository,
                      PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail(DEMO_ADMIN)) {
            log.info("Demo users already present, seeder skipped.");
            return;
        }
        // One BCrypt pass: hashing six identical passwords separately costs six
        // deliberate 100ms stalls at every start-up for no security gain.
        String passwordHash = passwordEncoder.encode(DEMO_PASSWORD);

        List<User> seeded = List.of(
                admin(passwordHash),
                customer(DEMO_TAKWA, "Takwa", "Ferchichi", "+216 55 214 780", passwordHash, 3),
                customer(DEMO_INES, "Ines", "Bouzid", "+216 24 771 309", passwordHash, 26),
                customer(DEMO_YASSINE, "Yassine", "Trabelsi", "+216 98 402 118", passwordHash, 51),
                customer(DEMO_SALMA, "Salma", "Gharbi", "+216 50 918 442", passwordHash, 118),
                blockedCustomer(DEMO_SAMI, "Sami", "Mejboud", "+216 26 845 903", passwordHash));
        userRepository.saveAll(seeded);

        auditLogRepository.saveAll(buildAuditHistory(seeded));
        announceDemoData();
    }

    private User admin(String passwordHash) {
        User user = base(DEMO_ADMIN, "Amine", "Ben Salah", "+216 74 903 118", passwordHash);
        user.setRole(Role.ADMIN);
        user.setLastLoginAt(Instant.now().minus(2, ChronoUnit.HOURS));
        return user;
    }

    /**
     * A seeded identity carries the sign-in time a real profile would show, so the
     * dashboard and the security panel have something honest to render on a fresh
     * clone instead of an empty "never signed in".
     */
    private User customer(String email, String firstName, String lastName, String phone,
                          String passwordHash, long hoursSinceLastLogin) {
        User user = base(email, firstName, lastName, phone, passwordHash);
        user.setRole(Role.CUSTOMER);
        user.setLastLoginAt(Instant.now().minus(hoursSinceLastLogin, ChronoUnit.HOURS));
        return user;
    }

    /**
     * The blocked demo identity keeps a last sign-in from before it was blocked:
     * a blocked account that looks like it never signed in would be a history this
     * platform could not have produced.
     */
    private User blockedCustomer(String email, String firstName, String lastName, String phone,
                                 String passwordHash) {
        User user = base(email, firstName, lastName, phone, passwordHash);
        user.setRole(Role.CUSTOMER);
        user.setStatus(UserStatus.BLOCKED);
        user.setLastLoginAt(Instant.now().minus(9, ChronoUnit.DAYS));
        return user;
    }

    private User base(String email, String firstName, String lastName, String phone, String passwordHash) {
        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        user.setPhone(phone);
        user.setPasswordHash(passwordHash);
        user.setRole(Role.CUSTOMER);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    /**
     * Thirty days of history across every action the admin screens can filter by,
     * attributed to the service that would really have produced it.
     */
    private List<AuditLog> buildAuditHistory(List<User> users) {
        List<User> customers = users.stream().filter(user -> user.getRole() == Role.CUSTOMER).toList();
        List<AuditLog> entries = new ArrayList<>(AUDIT_ROWS);
        Instant now = Instant.now();

        for (int index = 0; index < AUDIT_ROWS; index++) {
            User actor = customers.get(index % customers.size());
            int dayOffset = (int) Math.round((double) AUDIT_WINDOW_DAYS
                    * (AUDIT_ROWS - 1 - index) / (AUDIT_ROWS - 1));
            Instant occurredAt = now
                    .minus(dayOffset, ChronoUnit.DAYS)
                    .minus(7 + (index % 14), ChronoUnit.HOURS)
                    .minus((index * 11) % 59, ChronoUnit.MINUTES)
                    .truncatedTo(ChronoUnit.SECONDS);

            entries.add(entry(actor, index, occurredAt));
        }
        return entries;
    }

    /**
     * Cycles through the action catalogue so every filter in the admin console has
     * something behind it. The pattern is fixed rather than random so two fresh
     * clones produce a comparable-looking dashboard.
     */
    private AuditLog entry(User actor, int index, Instant occurredAt) {
        ActionTemplate action = ACTIONS.get(index % ACTIONS.size());
        AuditLog auditLog = new AuditLog();
        auditLog.setId(UUID.randomUUID().toString());
        auditLog.setAction(action.action());
        auditLog.setUserId(actor.getId());
        auditLog.setResource(action.resource());
        auditLog.setResourceId(action.resourceId());
        auditLog.setIpAddress(ipFor(index));
        auditLog.setCorrelationId("seed-" + UUID.randomUUID());
        auditLog.setResult(action.result());
        auditLog.setService(action.service());
        auditLog.setMessage(messageFor(action, actor));
        auditLog.setMetadata(action.metadata());
        auditLog.setCreatedAt(occurredAt);
        return auditLog;
    }

    private String ipFor(int index) {
        // Tunisian residential ranges, which is where these demo customers live.
        String[] prefixes = {"41.226.10.", "41.226.32.", "197.0.15.", "102.136.8."};
        return prefixes[index % prefixes.length] + (11 + (index * 13) % 200);
    }

    private String messageFor(ActionTemplate action, User actor) {
        return switch (action.action()) {
            case "LOGIN_SUCCESS" -> "Sign-in succeeded";
            case "LOGIN_FAILED" -> "Sign-in rejected: wrong password";
            case "USER_REGISTERED" -> "Customer registered";
            case "PROFILE_UPDATED" -> "Profile updated";
            case "TRANSFER_CREATED" -> "Transfer accepted and held as PENDING";
            case "TRANSFER_COMPLETED" -> "Transfer settled: balances debited and credited";
            case "TRANSFER_FAILED" -> "Transfer rejected before settlement: insufficient balance";
            case "FRAUD_DETECTED" -> "Transfer held for manual review after risk analysis";
            case "ACCOUNT_BLOCKED" -> "Account blocked pending fraud review";
            default -> "Operation recorded for " + actor.getEmail();
        };
    }

    private void announceDemoData() {
        StringBuilder banner = new StringBuilder();
        banner.append(System.lineSeparator());
        banner.append("==================================================================").append(System.lineSeparator());
        banner.append("=== FINOVA DEMO USERS ===").append(System.lineSeparator());
        banner.append("==================================================================").append(System.lineSeparator());
        banner.append("Password for every account below: ").append(DEMO_PASSWORD).append(System.lineSeparator());
        banner.append("Sign in at http://localhost:8080 (the Vue app on :5173).").append(System.lineSeparator());
        banner.append(System.lineSeparator());
        banner.append("  ADMIN     ").append(DEMO_ADMIN).append("   Amine Ben Salah").append(System.lineSeparator());
        banner.append("  CUSTOMER  ").append(DEMO_TAKWA).append("   Takwa Ferchichi")
                .append("   +216 55 214 780").append(System.lineSeparator());
        banner.append("  CUSTOMER  ").append(DEMO_INES).append("   Ines Bouzid")
                .append("   +216 24 771 309").append(System.lineSeparator());
        banner.append("  CUSTOMER  ").append(DEMO_YASSINE).append("   Yassine Trabelsi")
                .append("   +216 98 402 118").append(System.lineSeparator());
        banner.append("  CUSTOMER  ").append(DEMO_SALMA).append("   Salma Gharbi")
                .append("   +216 50 918 442").append(System.lineSeparator());
        banner.append("  BLOCKED   ").append(DEMO_SAMI).append("   Sami Mejboud")
                .append("   sign-in answers ACCOUNT_LOCKED").append(System.lineSeparator());
        banner.append(System.lineSeparator());
        banner.append("Seeded ").append(auditLogRepository.count()).append(" audit rows over the last ")
                .append(AUDIT_WINDOW_DAYS).append(" days.").append(System.lineSeparator());
        banner.append("Audit rows carry event_id = NULL on purpose: they were not Kafka").append(System.lineSeparator());
        banner.append("deliveries and must not occupy the consumer dedupe namespace.").append(System.lineSeparator());
        banner.append("==================================================================");
        log.info(banner.toString());
    }

    private record ActionTemplate(String action, String resource, String resourceId, String result,
                                  String service, Map<String, String> metadata) {
    }

    private static final List<ActionTemplate> ACTIONS = List.of(
            new ActionTemplate(AuditAction.LOGIN_SUCCESS.name(), "auth", "session", "SUCCESS",
                    "user-service", Map.of("device", "Chrome on Windows")),
            new ActionTemplate(AuditAction.TRANSFER_COMPLETED.name(), "transaction",
                    "TX-20260912-00007", "SUCCESS", "transaction-service",
                    Map.of("amount", "425.000", "currency", "TND")),
            new ActionTemplate(AuditAction.LOGIN_FAILED.name(), "auth", "session", "FAILURE",
                    "user-service", Map.of("reason", "wrong password")),
            new ActionTemplate(AuditAction.TRANSFER_CREATED.name(), "transaction",
                    "TX-20260912-00011", "SUCCESS", "transaction-service",
                    Map.of("amount", "180.500", "currency", "TND")),
            new ActionTemplate(AuditAction.PROFILE_UPDATED.name(), "user", "profile", "SUCCESS",
                    "user-service", Map.of("change", "phone")),
            new ActionTemplate(AuditAction.FRAUD_DETECTED.name(), "fraud-alert",
                    "FA-20260914-00003", "FAILURE", "fraud-service",
                    Map.of("riskScore", "82", "riskLevel", "HIGH")),
            new ActionTemplate(AuditAction.TRANSFER_FAILED.name(), "transaction",
                    "TX-20260916-00004", "FAILURE", "transaction-service",
                    Map.of("reason", "INSUFFICIENT_BALANCE")),
            new ActionTemplate(AuditAction.USER_REGISTERED.name(), "auth", "signup", "SUCCESS",
                    "user-service", Map.of("channel", "web")),
            new ActionTemplate(AuditAction.LOGIN_SUCCESS.name(), "auth", "session", "SUCCESS",
                    "user-service", Map.of("device", "Safari on iPhone")),
            new ActionTemplate(AuditAction.ACCOUNT_BLOCKED.name(), "account",
                    "TN58100001234567890123", "FAILURE", "account-service",
                    Map.of("reason", "fraud review")),
            new ActionTemplate(AuditAction.TRANSFER_COMPLETED.name(), "transaction",
                    "TX-20260921-00002", "SUCCESS", "transaction-service",
                    Map.of("amount", "96.300", "currency", "TND")),
            new ActionTemplate(AuditAction.LOGIN_SUCCESS.name(), "auth", "session", "SUCCESS",
                    "user-service", Map.of("device", "Chrome on Android")));
}
