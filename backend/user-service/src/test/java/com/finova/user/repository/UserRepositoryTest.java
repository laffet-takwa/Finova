package com.finova.user.repository;

import com.finova.common.domain.Role;
import com.finova.common.domain.UserStatus;
import com.finova.user.domain.AuditLog;
import com.finova.user.domain.RefreshToken;
import com.finova.user.domain.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against a real PostgreSQL so the migration, the Hibernate mapping and the
 * partial index are all verified together.
 * <p>
 * Hibernate validates the schema at context startup, so the fact that this class
 * starts at all is already the assertion that {@code V1__init.sql} matches the
 * entity mapping exactly — a column that drifted between the migration and the
 * entity fails the context before a single test method runs.
 * <p>
 * Skipped, not failed, when no Docker daemon is reachable.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class UserRepositoryTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("finova_users_test")
            .withUsername("finova")
            .withPassword("finova");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void cleanDatabase() {
        entityManager.createNativeQuery("delete from refresh_tokens").executeUpdate();
        entityManager.createNativeQuery("delete from audit_logs").executeUpdate();
        entityManager.createNativeQuery("delete from users").executeUpdate();
    }

    @Test
    @DisplayName("The unique index refuses a second identity with the same email")
    void shouldRejectADuplicateEmail() {
        userRepository.saveAndFlush(user("takwa@finova.dev", UserStatus.ACTIVE));

        assertThatThrownBy(() -> userRepository.saveAndFlush(user("takwa@finova.dev", UserStatus.ACTIVE)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(userRepository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("The JSONB metadata column round-trips a key set unchanged")
    void shouldRoundTripTheJsonbMetadata() {
        AuditLog entry = auditLog(UUID.randomUUID().toString(), "TRANSFER_COMPLETED",
                Map.of("riskScore", "82", "currency", "TND", "channel", "web"));
        auditLogRepository.saveAndFlush(entry);
        entityManager.clear();

        AuditLog reloaded = auditLogRepository.findById(entry.getId()).orElseThrow();
        assertThat(reloaded.getMetadata())
                .containsEntry("riskScore", "82")
                .containsEntry("currency", "TND")
                .containsEntry("channel", "web");
    }

    @Test
    @DisplayName("A JSONB column really is JSONB and not text")
    void shouldStoreMetadataAsJsonb() {
        auditLogRepository.saveAndFlush(auditLog(UUID.randomUUID().toString(), "PROFILE_UPDATED",
                Map.of("change", "password")));
        entityManager.clear();

        String type = (String) entityManager.createNativeQuery(
                        "select data_type from information_schema.columns "
                                + "where table_name = 'audit_logs' and column_name = 'metadata'")
                .getSingleResult();
        assertThat(type).isEqualTo("jsonb");
    }

    @Test
    @DisplayName("The partial unique index rejects a redelivered event")
    void shouldRejectADuplicateEventId() {
        String eventId = UUID.randomUUID().toString();
        auditLogRepository.saveAndFlush(auditLog(eventId, "TRANSFER_COMPLETED", Map.of()));
        auditLogRepository.saveAndFlush(auditLog(UUID.randomUUID().toString(), "LOGIN_SUCCESS", Map.of()));

        assertThatThrownBy(() -> {
            auditLogRepository.saveAndFlush(auditLog(eventId, "TRANSFER_FAILED", Map.of()));
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(auditLogRepository.count()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Rows without an event id are unconstrained, proving the index is partial")
    void shouldAllowManyRowsWithANullEventId() {
        auditLogRepository.saveAllAndFlush(List.of(
                auditLog(null, "LOGIN_SUCCESS", Map.of()),
                auditLog(null, "LOGIN_SUCCESS", Map.of()),
                auditLog(null, "PROFILE_UPDATED", Map.of())));

        assertThat(auditLogRepository.count()).isEqualTo(3L);
    }

    @Test
    @DisplayName("Every index the migration declares exists, and the audit one is partial")
    @SuppressWarnings("unchecked")
    void shouldExposeTheDeclaredIndexes() {
        List<Object[]> rows = entityManager.createNativeQuery(
                        "select indexname, indexdef from pg_indexes where tablename = 'audit_logs'")
                .getResultList();
        Map<String, String> definitions = new HashMap<>();
        for (Object[] row : rows) {
            definitions.put(row[0].toString(), row[1].toString());
        }

        assertThat(definitions).containsKeys("uq_audit_logs_event_id", "idx_audit_logs_action",
                "idx_audit_logs_user", "idx_audit_logs_correlation", "idx_audit_logs_created_at");
        assertThat(definitions.get("uq_audit_logs_event_id"))
                .contains("UNIQUE")
                .contains("event_id IS NOT NULL");
        assertThat(entityManager.createNativeQuery(
                        "select count(*) from pg_indexes where indexname = 'uq_users_email'")
                .getSingleResult()).isEqualTo(1L);
    }

    @Test
    @DisplayName("The mapping round-trips every user field, hash included")
    void shouldRoundTripTheUserMapping() {
        User saved = user("ines.bouzid@finova.dev", UserStatus.BLOCKED);
        saved.setPhone("+216 24 771 309");
        saved.setRole(Role.ADMIN);
        saved.setLastLoginAt(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        userRepository.saveAndFlush(saved);
        entityManager.clear();

        User reloaded = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getFirstName()).isEqualTo("Ines");
        assertThat(reloaded.getLastName()).isEqualTo("Bouzid");
        assertThat(reloaded.getEmail()).isEqualTo("ines.bouzid@finova.dev");
        assertThat(reloaded.getPhone()).isEqualTo("+216 24 771 309");
        assertThat(reloaded.getPasswordHash()).isEqualTo(saved.getPasswordHash());
        assertThat(reloaded.getRole()).isEqualTo(Role.ADMIN);
        assertThat(reloaded.getStatus()).isEqualTo(UserStatus.BLOCKED);
        assertThat(reloaded.getLastLoginAt()).isNotNull();
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Revoking a session is idempotent and reports how many rows it touched")
    void shouldRevokeASessionOnlyOnce() {
        RefreshToken token = new RefreshToken();
        token.setId(UUID.randomUUID().toString());
        token.setUserId("takwa");
        token.setTokenId("jti-1");
        token.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        token.setRevoked(false);
        token.setCreatedAt(Instant.now());
        refreshTokenRepository.saveAndFlush(token);

        assertThat(refreshTokenRepository.revokeByTokenId("jti-1")).isEqualTo(1);
        assertThat(refreshTokenRepository.revokeByTokenId("jti-1")).isZero();
        Optional<RefreshToken> reloaded = refreshTokenRepository.findByTokenId("jti-1");
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().isRevoked()).isTrue();
    }

    private User user(String email, UserStatus status) {
        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setFirstName("Takwa");
        user.setLastName("Ferchichi");
        user.setEmail(email);
        user.setPhone("+216 55 214 780");
        user.setPasswordHash("$2a$10$8K1p/a0dURXAm7QiTRqjOuqZ0lT7kQvZ0ZQ6b6eQ4mXhZ8kZ2r7a");
        user.setRole(Role.CUSTOMER);
        user.setStatus(status);
        return user;
    }

    private AuditLog auditLog(String eventId, String action, Map<String, String> metadata) {
        AuditLog entry = new AuditLog();
        entry.setId(UUID.randomUUID().toString());
        entry.setEventId(eventId);
        entry.setAction(action);
        entry.setUserId("3f6d9a1c-4b7e-4f0a-9c2d-8e5f1a2b3c4d");
        entry.setResource("transaction");
        entry.setResourceId("TX-20260912-00007");
        entry.setIpAddress("41.226.10.37");
        entry.setCorrelationId("corr-" + UUID.randomUUID());
        entry.setResult("SUCCESS");
        entry.setService("transaction-service");
        entry.setMessage("Transfer settled");
        entry.setMetadata(metadata.isEmpty() ? null : metadata);
        entry.setCreatedAt(Instant.now().minus(3, ChronoUnit.HOURS));
        return entry;
    }
}
