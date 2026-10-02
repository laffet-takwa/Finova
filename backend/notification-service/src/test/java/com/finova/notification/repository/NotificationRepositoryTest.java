package com.finova.notification.repository;

import com.finova.common.domain.NotificationType;
import com.finova.notification.domain.Notification;
import com.finova.notification.domain.NotificationCategory;
import com.finova.notification.domain.NotificationSeverity;
import com.finova.notification.service.RetentionJob;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against a real PostgreSQL so the migration, the Hibernate mapping and the
 * partial indexes are all verified together.
 * <p>
 * Hibernate validates the schema at context startup, so the fact that this class
 * starts at all is already the assertion that {@code V1__init.sql} matches the
 * entity mapping exactly.
 * <p>
 * Skipped, not failed, when no Docker daemon is reachable.
 */
@SpringBootTest(properties = "finova.notifications.retention-days=30")
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class NotificationRepositoryTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("finova_notifications_test")
            .withUsername("finova")
            .withPassword("finova");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private RetentionJob retentionJob;

    @Value("${finova.notifications.retention-days:90}")
    private int retentionDays;

    @BeforeEach
    void cleanDatabase() {
        entityManager.createNativeQuery("delete from notifications").executeUpdate();
        entityManager.createNativeQuery("delete from outbox_event").executeUpdate();
    }

    @Test
    @DisplayName("The partial unique index rejects a redelivered source event")
    void shouldRejectADuplicateSourceEventId() {
        String sourceEventId = UUID.randomUUID().toString();
        notificationRepository.saveAndFlush(notification("takwa-1", "evt-" + UUID.randomUUID(), null));
        notificationRepository.saveAndFlush(notification("takwa-2", sourceEventId, null));

        assertThatThrownBy(() -> {
            notificationRepository.saveAndFlush(notification("takwa-3", sourceEventId, null));
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(notificationRepository.count()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Rows without a source event id are not constrained, proving the index is partial")
    void shouldAllowManyRowsWithANullSourceEventId() {
        notificationRepository.saveAndFlush(notification("seed-1", null, null));
        notificationRepository.saveAndFlush(notification("seed-2", null, null));
        notificationRepository.saveAndFlush(notification("seed-3", null, null));

        assertThat(notificationRepository.countByUserId("seed-1")).isEqualTo(1L);
        assertThat(notificationRepository.count()).isEqualTo(3L);
    }

    @Test
    @DisplayName("Both partial indexes exist exactly as the migration declares them")
    @SuppressWarnings("unchecked")
    void shouldExposeThePartialIndexes() {
        List<Object[]> indexes = entityManager.createNativeQuery(
                        "select indexname, indexdef from pg_indexes where tablename = 'notifications'")
                .getResultList();

        Map<String, String> definitions = new HashMap<>();
        for (Object[] index : indexes) {
            definitions.put(index[0].toString(), index[1].toString());
        }

        assertThat(definitions).containsKeys(
                "idx_notification_user_unread",
                "uq_notification_source_event",
                "idx_notification_user_created");

        assertThat(definitions.get("idx_notification_user_unread")).contains("read = false");
        assertThat(definitions.get("uq_notification_source_event"))
                .contains("UNIQUE")
                .contains("source_event_id IS NOT NULL");
        assertThat(definitions.get("idx_notification_user_created")).contains("created_at DESC");
    }

    @Test
    @DisplayName("An owner scoped update touches one row and leaves the other customer alone")
    void shouldScopeUpdatesToTheOwner() {
        notificationRepository.saveAndFlush(notification("owner-a", null, null));
        notificationRepository.saveAndFlush(notification("owner-b", null, null));

        int updated = notificationRepository.markReadIfOwnedAndUnread(
                idOf("owner-a"), "owner-a", Instant.now());

        assertThat(updated).isEqualTo(1);
        assertThat(notificationRepository.markReadIfOwnedAndUnread(
                idOf("owner-a"), "owner-b", Instant.now())).isZero();
    }

    @Test
    @DisplayName("Retention removes read rows past the window and keeps unread ones")
    void shouldPurgeOnlyReadRowsOlderThanTheWindow() {
        Notification oldRead = notification("takwa", null, Instant.now().minus(retentionDays + 5L, ChronoUnit.DAYS));
        oldRead.setRead(true);
        oldRead.setReadAt(Instant.now().minus(retentionDays + 4L, ChronoUnit.DAYS));
        Notification oldUnread = notification("takwa", null, Instant.now().minus(retentionDays + 5L, ChronoUnit.DAYS));
        Notification recentRead = notification("takwa", null, Instant.now().minus(2, ChronoUnit.DAYS));
        recentRead.setRead(true);
        recentRead.setReadAt(Instant.now().minus(2, ChronoUnit.DAYS));
        notificationRepository.saveAllAndFlush(List.of(oldRead, oldUnread, recentRead));

        int deleted = retentionJob.purgeExpired();

        assertThat(deleted).isEqualTo(1);
        assertThat(notificationRepository.findById(oldRead.getId())).isEmpty();
        assertThat(notificationRepository.findById(oldUnread.getId())).isPresent();
        assertThat(notificationRepository.findById(recentRead.getId())).isPresent();
    }

    @Test
    @DisplayName("The mapping round-trips every stored field")
    void shouldRoundTripTheEntityMapping() {
        String sourceEventId = UUID.randomUUID().toString();
        Notification stored = notification("takwa", sourceEventId, Instant.now());
        stored.setAmount(new BigDecimal("250.000"));
        stored.setCurrency("TND");
        notificationRepository.saveAndFlush(stored);
        entityManager.clear();

        Notification reloaded = notificationRepository.findById(stored.getId()).orElseThrow();
        assertThat(reloaded.getUserId()).isEqualTo("takwa");
        assertThat(reloaded.getType()).isEqualTo(NotificationType.TRANSFER_COMPLETED);
        assertThat(reloaded.getCategory()).isEqualTo(NotificationCategory.TRANSACTIONS);
        assertThat(reloaded.getSeverity()).isEqualTo(NotificationSeverity.SUCCESS);
        assertThat(reloaded.getAmount()).isEqualByComparingTo("250.000");
        assertThat(reloaded.getCurrency()).isEqualTo("TND");
        assertThat(reloaded.getSourceEventId()).isEqualTo(sourceEventId);
        assertThat(reloaded.isRead()).isFalse();
        assertThat(reloaded.getVersion()).isZero();
    }

    private String idOf(String userId) {
        return notificationRepository.findAll(
                        NotificationSpecifications.forUser(userId, null))
                .get(0).getId();
    }

    private Notification notification(String userId, String sourceEventId, Instant createdAt) {
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID().toString());
        notification.setUserId(userId);
        notification.setType(NotificationType.TRANSFER_COMPLETED);
        notification.setCategory(NotificationCategory.TRANSACTIONS);
        notification.setSeverity(NotificationSeverity.SUCCESS);
        notification.setTitle("Transfer completed");
        notification.setMessage("Your transfer of 250.000 TND to account •••• 4321 was successful.");
        notification.setTransactionId(UUID.randomUUID().toString());
        notification.setReference("TX-20261001-00001");
        notification.setAmount(new BigDecimal("250.000"));
        notification.setCurrency("TND");
        notification.setRead(false);
        notification.setCreatedAt(createdAt == null ? Instant.now() : createdAt);
        notification.setCorrelationId("corr-" + UUID.randomUUID());
        notification.setSourceService("transaction-service");
        notification.setSourceEventId(sourceEventId);
        return notification;
    }
}