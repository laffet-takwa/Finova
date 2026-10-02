package com.finova.notification.mapper;

import com.finova.common.domain.Currency;
import com.finova.common.domain.NotificationType;
import com.finova.notification.domain.Notification;
import com.finova.notification.domain.NotificationCategory;
import com.finova.notification.domain.NotificationSeverity;
import com.finova.notification.dto.AdminNotificationResponse;
import com.finova.notification.dto.NotificationResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two views of one row are genuinely different contracts, and the only place a
 * regression could sneak an owner field into the customer inbox is this mapping.
 */
class NotificationMapperTest {

    private static final String OWNER = "3f6d9a1c-4b7e-4f0a-9c2d-8e5f1a2b3c4d";

    private final NotificationMapper mapper = new NotificationMapperImpl();

    @Test
    @DisplayName("The owner hint keeps the first eight characters and an ellipsis")
    void shouldMaskTheOwnerHint() {
        assertThat(AdminHints.maskUserId(OWNER)).isEqualTo("3f6d9a1c…");
        assertThat(AdminHints.maskUserId("demo-takwa")).isEqualTo("demo-tak…");
        assertThat(AdminHints.maskUserId("123456789")).isEqualTo("12345678…");
        assertThat(AdminHints.maskUserId("short")).isEqualTo("short…");
        assertThat(AdminHints.maskUserId(null)).isNull();
        assertThat(AdminHints.maskUserId("  ")).isNull();
    }

    @Test
    @DisplayName("No string property of any target is masked, only the owner hint")
    void shouldNotMaskOrdinaryStrings() {
        AdminNotificationResponse admin = mapper.toAdminResponse(notification());
        NotificationResponse customer = mapper.toResponse(notification());

        assertThat(admin.id()).isEqualTo("n1");
        assertThat(admin.title()).isEqualTo("Transfer completed");
        assertThat(admin.message()).isEqualTo("Your transfer of 250.000 TND to account •••• 4321 was successful.");
        assertThat(admin.reference()).isEqualTo("TX-20261001-00001");
        assertThat(admin.correlationId()).isEqualTo("corr-1");
        assertThat(customer.id()).isEqualTo("n1");
        assertThat(customer.title()).isEqualTo("Transfer completed");
        assertThat(customer.reference()).isEqualTo("TX-20261001-00001");
    }

    @Test
    @DisplayName("The admin row names the owner and carries the hint")
    void shouldExposeTheOwnerOnTheAdminRow() {
        AdminNotificationResponse row = mapper.toAdminResponse(notification());

        assertThat(row.userId()).isEqualTo(OWNER);
        assertThat(row.userDisplayHint()).isEqualTo("3f6d9a1c…");
        assertThat(row.type()).isEqualTo(NotificationType.TRANSFER_COMPLETED);
        assertThat(row.category()).isEqualTo(NotificationCategory.TRANSACTIONS);
        assertThat(row.severity()).isEqualTo(NotificationSeverity.SUCCESS);
        assertThat(row.currency()).isEqualTo(Currency.TND);
        assertThat(row.correlationId()).isEqualTo("corr-1");
        assertThat(row.amount()).isEqualByComparingTo("250.000");
    }

    @Test
    @DisplayName("The customer row has no owner, no hint and no correlation id")
    void shouldKeepTheOwnerOutOfTheCustomerRow() {
        NotificationResponse row = mapper.toResponse(notification());

        assertThat(NotificationResponse.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("userId", "userDisplayHint", "correlationId");
        assertThat(row.id()).isEqualTo("n1");
        assertThat(row.currency()).isEqualTo("TND");
    }

    @Test
    @DisplayName("An unrecognised currency degrades to null instead of breaking the feed")
    void shouldDegradeAnUnknownCurrency() {
        assertThat(AdminHints.toCurrency("tnd")).isEqualTo(Currency.TND);
        assertThat(AdminHints.toCurrency(" EUR ")).isEqualTo(Currency.EUR);
        assertThat(AdminHints.toCurrency("GBP")).isNull();
        assertThat(AdminHints.toCurrency("")).isNull();
        assertThat(AdminHints.toCurrency(null)).isNull();
    }

    private Notification notification() {
        Notification notification = new Notification();
        notification.setId("n1");
        notification.setUserId(OWNER);
        notification.setType(NotificationType.TRANSFER_COMPLETED);
        notification.setCategory(NotificationCategory.TRANSACTIONS);
        notification.setSeverity(NotificationSeverity.SUCCESS);
        notification.setTitle("Transfer completed");
        notification.setMessage("Your transfer of 250.000 TND to account •••• 4321 was successful.");
        notification.setTransactionId("tx-1");
        notification.setReference("TX-20261001-00001");
        notification.setAmount(new BigDecimal("250.000"));
        notification.setCurrency("TND");
        notification.setRead(false);
        notification.setCreatedAt(Instant.now());
        notification.setCorrelationId("corr-1");
        notification.setSourceService("transaction-service");
        return notification;
    }
}