package com.finova.fraud.service;

import com.finova.fraud.config.FraudProperties;
import com.finova.fraud.repository.VelocityWindowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Drops velocity windows nobody has touched for a long time.
 * <p>
 * A window is only useful while its account is active. Accounts that stopped transferring years ago
 * would otherwise keep one document each forever, and a window that old can no longer influence a
 * score because the burst rule only looks inside the configured window.
 */
@Component
public class VelocityWindowCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(VelocityWindowCleanupJob.class);

    private final VelocityWindowRepository windowRepository;
    private final FraudProperties properties;

    public VelocityWindowCleanupJob(VelocityWindowRepository windowRepository,
                                    FraudProperties properties) {
        this.windowRepository = windowRepository;
        this.properties = properties;
    }

    @Scheduled(cron = "${finova.fraud.velocity-state.cleanup-cron:0 17 * * * *}")
    public void purgeStaleWindows() {
        int retentionDays = properties.getVelocityState().getRetentionDays();
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        long removed = windowRepository.deleteByUpdatedAtBefore(cutoff);
        if (removed > 0) {
            log.info("Purged {} velocity windows untouched since {}", removed, cutoff);
        }
    }
}
