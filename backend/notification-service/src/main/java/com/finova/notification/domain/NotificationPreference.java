package com.finova.notification.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Per-user delivery preferences. The primary key is the user id itself, so the
 * row is created lazily on the first read and never joined to anything else.
 */
@Entity
@Table(name = "notification_preferences")
@Getter
@Setter
@NoArgsConstructor
public class NotificationPreference {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled = true;

    @Column(name = "push_enabled", nullable = false)
    private boolean pushEnabled = true;

    @Column(name = "in_app_enabled", nullable = false)
    private boolean inAppEnabled = true;

    @Column(name = "transfer_alerts", nullable = false)
    private boolean transferAlerts = true;

    @Column(name = "security_alerts", nullable = false)
    private boolean securityAlerts = true;

    @Column(name = "marketing_emails", nullable = false)
    private boolean marketingEmails = false;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static NotificationPreference defaults(String userId, Instant now) {
        NotificationPreference preference = new NotificationPreference();
        preference.setId(userId);
        preference.setUpdatedAt(now);
        return preference;
    }
}