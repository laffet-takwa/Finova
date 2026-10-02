package com.finova.notification.service;

import com.finova.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Retention policy for the inbox.
 * <p>
 * Only entries the user has already opened are eligible for deletion. An unread
 * entry is an obligation, not clutter: dropping it would silently lose a money
 * movement or a security alert nobody has acted on yet.
 */
@Component
public class RetentionJob {

    private static final Logger log = LoggerFactory.getLogger(RetentionJob.class);

    private final NotificationRepository notificationRepository;
    private final int retentionDays;

    public RetentionJob(NotificationRepository notificationRepository,
                        @Value("${finova.notifications.retention-days:90}") int retentionDays) {
        this.notificationRepository = notificationRepository;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${finova.notifications.retention-cron:0 30 3 * * *}")
    @Transactional
    public int purgeExpired() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deleted = notificationRepository.deleteByReadTrueAndCreatedAtBefore(cutoff);
        if (deleted > 0) {
            log.info("Retention purge removed {} read notifications older than {} days (cutoff={})",
                    deleted, retentionDays, cutoff);
        }
        return deleted;
    }
}