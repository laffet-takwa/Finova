package com.finova.notification.service;

import com.finova.common.domain.NotificationType;
import com.finova.common.error.BusinessException;
import com.finova.common.error.ErrorCode;
import com.finova.common.web.PageResponse;
import com.finova.notification.domain.Notification;
import com.finova.notification.domain.NotificationCategory;
import com.finova.notification.domain.NotificationPreference;
import com.finova.notification.domain.NotificationSeverity;
import com.finova.notification.dto.NotificationFilters;
import com.finova.notification.dto.NotificationResponse;
import com.finova.notification.dto.NotificationStatsResponse;
import com.finova.notification.dto.PreferenceResponse;
import com.finova.notification.dto.PreferenceUpdateRequest;
import com.finova.notification.dto.UnreadCountResponse;
import com.finova.notification.mapper.NotificationMapper;
import com.finova.notification.mapper.NotificationMapperImpl;
import com.finova.notification.repository.NotificationPreferenceRepository;
import com.finova.notification.repository.NotificationRepository;
import com.finova.notification.repository.NotificationSpecifications;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceTest {

    private static final String CALLER = "demo-takwa";
    private static final String OTHER = "demo-ines";

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationPreferenceRepository preferenceRepository;

    private final NotificationMapper mapper = new NotificationMapperImpl();
    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository, preferenceRepository, mapper);
    }

    @Test
    @DisplayName("Listing is scoped to the caller's id and the filters are passed through")
    @SuppressWarnings("unchecked")
    void shouldScopeListToTheCallerAndForwardFilters() {
        NotificationFilters filters = new NotificationFilters(
                "TRANSFER_COMPLETED", "SECURITY", true, "salary",
                Instant.now().minus(7, ChronoUnit.DAYS), Instant.now(), 2, 50);
        when(notificationRepository.findAll(anySpecification(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(notification("n1", CALLER))));

        PageResponse<NotificationResponse> page = service.list(CALLER, filters);

        ArgumentCaptor<Specification<Notification>> specification =
                ArgumentCaptor.forClass((Class<Specification<Notification>>) (Class<?>) Specification.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findAll(specification.capture(), pageable.capture());

        // The specification is a value type, so equality proves the owner was baked
        // into the query and that the filters reached the repository unchanged.
        assertThat(specification.getValue())
                .isEqualTo(NotificationSpecifications.forUser(CALLER, filters));
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();
        assertThat(page.content()).hasSize(1);
    }

    @Test
    @DisplayName("The filters carry no user id, so ownership cannot be widened from the client")
    void shouldNotExposeAUserIdFilter() {
        assertThat(NotificationFilters.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("userId")
                .containsExactly("type", "category", "unreadOnly", "search", "from", "to", "page", "size");
    }

    @Test
    @DisplayName("An oversized page is clamped instead of trusted")
    void shouldClampOversizedPages() {
        NotificationFilters filters = new NotificationFilters(null, null, false, null, null, null, 0, 5000);
        when(notificationRepository.findAll(anySpecification(), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.list(CALLER, filters);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findAll(anySpecification(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(NotificationService.MAX_PAGE_SIZE);
    }

    @Test
    @DisplayName("The unread list is bounded and scoped to the caller")
    void shouldBoundAndScopeTheUnreadList() {
        when(notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(
                eq(CALLER), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(notification("n1", CALLER), notification("n2", CALLER))));

        assertThat(service.unread(CALLER, 999)).hasSize(2);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findByUserIdAndReadFalseOrderByCreatedAtDesc(eq(CALLER), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(NotificationService.MAX_UNREAD_LIMIT);
    }

    @Test
    @DisplayName("The unread list falls back to the default size for a nonsensical limit")
    void shouldUseDefaultLimitWhenLimitIsNotPositive() {
        when(notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(
                eq(CALLER), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.unread(CALLER, 0);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findByUserIdAndReadFalseOrderByCreatedAtDesc(eq(CALLER), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(NotificationService.DEFAULT_UNREAD_LIMIT);
    }

    @Test
    @DisplayName("Marking another customer's notification read is a 404, not a leak")
    void shouldRejectMarkReadOnAnotherUsersNotification() {
        when(notificationRepository.findByIdAndUserId("n-other", CALLER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(CALLER, "n-other"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
    }

    @Test
    @DisplayName("The mark-read update is scoped by the owner in its WHERE clause")
    void shouldScopeTheMarkReadUpdateToTheCaller() {
        when(notificationRepository.markReadIfOwnedAndUnread(eq("n1"), eq(CALLER), any(Instant.class)))
                .thenReturn(1);
        Notification read = notification("n1", CALLER);
        read.setRead(true);
        read.setReadAt(Instant.now());
        when(notificationRepository.findByIdAndUserId("n1", CALLER)).thenReturn(Optional.of(read));

        NotificationResponse response = service.markRead(CALLER, "n1");

        verify(notificationRepository).markReadIfOwnedAndUnread(eq("n1"), eq(CALLER), any(Instant.class));
        assertThat(response.read()).isTrue();
        assertThat(response.readAt()).isNotNull();
    }

    @Test
    @DisplayName("Marking an already-read notification is idempotent, not an error")
    void shouldReturnAlreadyReadNotificationUnchanged() {
        when(notificationRepository.markReadIfOwnedAndUnread(eq("n1"), eq(CALLER), any(Instant.class)))
                .thenReturn(0);
        Instant originalReadAt = Instant.now().minus(2, ChronoUnit.HOURS);
        Notification alreadyRead = notification("n1", CALLER);
        alreadyRead.setRead(true);
        alreadyRead.setReadAt(originalReadAt);
        when(notificationRepository.findByIdAndUserId("n1", CALLER)).thenReturn(Optional.of(alreadyRead));

        NotificationResponse response = service.markRead(CALLER, "n1");

        assertThat(response.read()).isTrue();
        assertThat(response.readAt()).isEqualTo(originalReadAt);
    }

    @Test
    @DisplayName("Mark all read returns how many entries actually changed")
    void shouldReturnTheMarkAllReadCount() {
        when(notificationRepository.markAllReadForUser(eq(CALLER), any(Instant.class))).thenReturn(7);

        assertThat(service.markAllRead(CALLER).updated()).isEqualTo(7);
        verify(notificationRepository).markAllReadForUser(eq(CALLER), any(Instant.class));
    }

    @Test
    @DisplayName("The unread counters always carry all three folders")
    void shouldGroupUnreadCountByCategory() {
        when(notificationRepository.countByUserIdAndReadFalse(CALLER)).thenReturn(6L);
        when(notificationRepository.countUnreadByCategory(CALLER)).thenReturn(List.of(
                new NotificationRepository.CategoryCount() {
                    @Override
                    public NotificationCategory getCategory() {
                        return NotificationCategory.SECURITY;
                    }

                    @Override
                    public long getTotal() {
                        return 2L;
                    }
                }));

        UnreadCountResponse response = service.unreadCount(CALLER);

        assertThat(response.unread()).isEqualTo(6L);
        assertThat(response.byCategory())
                .containsEntry("TRANSACTIONS", 0L)
                .containsEntry("SECURITY", 2L)
                .containsEntry("SYSTEM", 0L)
                .hasSize(3);
    }

    @Test
    @DisplayName("Statistics are zero filled over a seven day window")
    void shouldBuildZeroFilledStatistics() {
        when(notificationRepository.countByUserId(CALLER)).thenReturn(52L);
        when(notificationRepository.countByUserIdAndReadFalse(CALLER)).thenReturn(6L);
        when(notificationRepository.countAllByType(CALLER)).thenReturn(List.of(typeCount(NotificationType.TRANSFER_COMPLETED, 30)));
        when(notificationRepository.countAllByCategory(CALLER)).thenReturn(List.of(categoryCount(NotificationCategory.TRANSACTIONS, 40)));
        Instant today = Instant.now().truncatedTo(ChronoUnit.DAYS);
        when(notificationRepository.findCreatedSince(eq(CALLER), any(Instant.class)))
                .thenReturn(List.of(today, today, today.minus(3, ChronoUnit.DAYS)));
        when(notificationRepository.findUnreadCreatedSince(eq(CALLER), any(Instant.class)))
                .thenReturn(List.of(today));

        NotificationStatsResponse stats = service.stats(CALLER);

        assertThat(stats.total()).isEqualTo(52L);
        assertThat(stats.unread()).isEqualTo(6L);
        assertThat(stats.byType()).hasSize(NotificationType.values().length)
                .containsEntry("TRANSFER_COMPLETED", 30L)
                .containsEntry("SECURITY_ALERT", 0L);
        assertThat(stats.byCategory()).hasSize(NotificationCategory.values().length)
                .containsEntry("TRANSACTIONS", 40L)
                .containsEntry("SYSTEM", 0L);
        assertThat(stats.last7Days()).hasSize(7);
        assertThat(stats.last7Days().get(6).count()).isEqualTo(3L);
        assertThat(stats.last7Days().get(3).count()).isEqualTo(1L);
        assertThat(stats.last7Days().get(0).count()).isZero();
        assertThat(stats.unreadTrend()).hasSize(7);
        assertThat(stats.unreadTrend().get(6).count()).isEqualTo(1L);
        assertThat(stats.unreadTrend().get(5).count()).isZero();
    }

    @Test
    @DisplayName("Reading one notification of another customer is a 404")
    void shouldRejectGetOnAnotherUsersNotification() {
        when(notificationRepository.findByIdAndUserId("n-other", CALLER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(CALLER, "n-other"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
    }

    @Test
    @DisplayName("Preferences are materialised on the first read")
    void shouldCreateDefaultPreferencesOnFirstRead() {
        when(preferenceRepository.findById(CALLER)).thenReturn(Optional.empty());
        when(preferenceRepository.save(any(NotificationPreference.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PreferenceResponse response = service.getPreferences(CALLER);

        assertThat(response.emailEnabled()).isTrue();
        assertThat(response.inAppEnabled()).isTrue();
        assertThat(response.transferAlerts()).isTrue();
        assertThat(response.securityAlerts()).isTrue();
        assertThat(response.pushEnabled()).isTrue();
        assertThat(response.marketingEmails()).isFalse();
        verify(preferenceRepository).save(any(NotificationPreference.class));
    }

    @Test
    @DisplayName("Updating preferences replaces all six switches")
    void shouldReplaceAllPreferenceFlags() {
        NotificationPreference stored = NotificationPreference.defaults(CALLER, Instant.now());
        when(preferenceRepository.findById(CALLER)).thenReturn(Optional.of(stored));
        when(preferenceRepository.save(any(NotificationPreference.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PreferenceResponse response = service.updatePreferences(CALLER, new PreferenceUpdateRequest(
                false, true, false, false, true, true));

        assertThat(response.emailEnabled()).isFalse();
        assertThat(response.inAppEnabled()).isFalse();
        assertThat(response.transferAlerts()).isFalse();
        assertThat(response.marketingEmails()).isTrue();
        verify(preferenceRepository, times(1)).save(stored);
    }

    @Test
    @DisplayName("The read path never falls back to an unscoped lookup")
    void shouldNeverQueryWithoutAnOwner() {
        when(notificationRepository.findByIdAndUserId("n1", CALLER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(CALLER, "n1")).isInstanceOf(BusinessException.class);
        verify(notificationRepository, never()).findById(anyString());
    }

    /**
     * {@code JpaRepository} also inherits {@code findAll(Example, Pageable)}, so the
     * matcher needs an explicit type argument to select the specification overload.
     */
    private static Specification<Notification> anySpecification() {
        return ArgumentMatchers.<Specification<Notification>>any();
    }

    private Notification notification(String id, String userId) {
        Notification notification = new Notification();
        notification.setId(id);
        notification.setUserId(userId);
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
        return notification;
    }

    private NotificationRepository.TypeCount typeCount(NotificationType type, long total) {
        return new NotificationRepository.TypeCount() {
            @Override
            public NotificationType getType() {
                return type;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }

    private NotificationRepository.CategoryCount categoryCount(NotificationCategory category, long total) {
        return new NotificationRepository.CategoryCount() {
            @Override
            public NotificationCategory getCategory() {
                return category;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }

    @Test
    @DisplayName("A page the repository returns is mapped without leaking the owner")
    void shouldMapPageWithoutTheOwner() {
        when(notificationRepository.findAll(anySpecification(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(notification("n1", CALLER))));

        PageResponse<NotificationResponse> page = service.list(CALLER,
                new NotificationFilters(null, null, false, null, null, null, 0, 20));

        assertThat(NotificationResponse.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("userId");
        assertThat(page.content().get(0).id()).isEqualTo("n1");
        assertThat(page.totalElements()).isEqualTo(1L);
        assertThat(page.first()).isTrue();
        assertThat(page.last()).isTrue();
    }

    @Test
    @DisplayName("The owner id is never taken from a caller supplied string")
    void shouldIgnoreAnyOwnerLookingArgument() {
        NotificationFilters filters = new NotificationFilters(null, null, false, null, null, null, 0, 20);
        when(notificationRepository.findAll(anySpecification(), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.list(OTHER, filters);

        verify(notificationRepository).findAll(
                eq(NotificationSpecifications.forUser(OTHER, filters)), any(Pageable.class));
        assertThat(NotificationSpecifications.forUser(OTHER, filters))
                .isNotEqualTo(NotificationSpecifications.forUser(CALLER, filters));
    }
}