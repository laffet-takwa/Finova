package com.finova.user.service;

import com.finova.user.repository.AuditLogRepository;
import com.finova.user.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Nightly housekeeping for the two tables that only ever grow.
 * <p>
 * The audit trail is a security record, so the window is deliberately long and the
 * job is deliberately plain: no partitioning, no archive tier, no soft delete. A
 * trail that quietly loses rows is not a trail.
 */
@Component
public class RetentionJob {

    private static final Logger log = LoggerFactory.getLogger(RetentionJob.class);

    private final AuditLogRepository auditLogRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final int auditRetentionDays;

    public RetentionJob(AuditLogRepository auditLogRepository,
                        RefreshTokenRepository refreshTokenRepository,
                        @Value("${finova.users.audit-retention-days:365}") int auditRetentionDays) {
        this.auditLogRepository = auditLogRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.auditRetentionDays = auditRetentionDays;
    }

    @Scheduled(cron = "${finova.users.audit-purge-cron:0 0 4 * * *}")
    @Transactional
    public void purge() {
        Instant auditCutoff = Instant.now().minus(auditRetentionDays, ChronoUnit.DAYS);
        int auditRows = auditLogRepository.deleteCreatedBefore(auditCutoff);
        int sessions = refreshTokenRepository.deleteExpiredBefore(Instant.now());
        if (auditRows > 0 || sessions > 0) {
            log.info("Purged {} audit rows older than {} days and {} expired sessions",
                    auditRows, auditRetentionDays, sessions);
        }
    }
}
