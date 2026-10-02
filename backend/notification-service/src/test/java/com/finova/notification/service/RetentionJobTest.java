package com.finova.notification.service;

import com.finova.notification.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The purge window and the deleted-row count.
 * <p>
 * Only read rows older than the window are eligible, which is expressed by the
 * single derived delete the job issues: {@code read = true AND created_at < cutoff}.
 * The data-level proof that unread rows survive is asserted against a real
 * PostgreSQL in {@code NotificationRepositoryTest}, where the migration and the
 * entity mapping are both in play.
 */
@ExtendWith(MockitoExtension.class)
class RetentionJobTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Test
    @DisplayName("The cutoff is the retention window ago, and the count is returned")
    void shouldDeleteReadNotificationsOlderThanTheWindow() {
        when(notificationRepository.deleteByReadTrueAndCreatedAtBefore(any()))
                .thenReturn(12);
        RetentionJob job = new RetentionJob(notificationRepository, 90);

        Instant before = Instant.now();
        int deleted = job.purgeExpired();
        Instant after = Instant.now();

        assertThat(deleted).isEqualTo(12);
        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(notificationRepository).deleteByReadTrueAndCreatedAtBefore(cutoff.capture());
        assertThat(cutoff.getValue())
                .isBetween(before.minus(90, ChronoUnit.DAYS),
                        after.minus(90, ChronoUnit.DAYS));
        assertThat(Duration.between(cutoff.getValue(), Instant.now()).toDays()).isBetween(89L, 90L);
    }

    @Test
    @DisplayName("The window is configurable and drives the cutoff")
    void shouldHonourAConfiguredWindow() {
        when(notificationRepository.deleteByReadTrueAndCreatedAtBefore(any()))
                .thenReturn(0);
        RetentionJob job = new RetentionJob(notificationRepository, 30);

        assertThat(job.purgeExpired()).isZero();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(notificationRepository).deleteByReadTrueAndCreatedAtBefore(cutoff.capture());
        assertThat(Duration.between(cutoff.getValue(), Instant.now()).toDays()).isBetween(29L, 30L);
    }
}