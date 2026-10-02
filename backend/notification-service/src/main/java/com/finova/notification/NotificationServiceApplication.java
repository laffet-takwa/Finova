package com.finova.notification;

import com.finova.common.security.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Notification service: the terminal consumer of the transfer flow.
 * <p>
 * It reads {@code transaction.completed}, {@code transaction.failed},
 * {@code transaction.flagged} and {@code account.blocked}, and never reads
 * {@code transaction.approved}: an approval is a decision, not a movement.
 * <p>
 * JPA auditing is enabled by {@code JpaAuditingConfig}, not here, so that a web slice
 * test can start without a persistence context.
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(JwtProperties.class)
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}