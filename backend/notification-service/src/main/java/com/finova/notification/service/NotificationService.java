package com.finova.notification.service;

import com.finova.common.domain.NotificationType;
import com.finova.common.error.BusinessException;
import com.finova.common.error.ErrorCode;
import com.finova.common.web.PageResponse;
import com.finova.notification.domain.Notification;
import com.finova.notification.domain.NotificationCategory;
import com.finova.notification.domain.NotificationPreference;
import com.finova.notification.dto.AdminNotificationResponse;
import com.finova.notification.dto.DailyCount;
import com.finova.notification.dto.MarkAllReadResponse;
import com.finova.notification.dto.NotificationFilters;
import com.finova.notification.dto.NotificationResponse;
import com.finova.notification.dto.NotificationStatsResponse;
import com.finova.notification.dto.PreferenceResponse;
import com.finova.notification.dto.PreferenceUpdateRequest;
import com.finova.notification.dto.UnreadCountResponse;
import com.finova.notification.mapper.NotificationMapper;
import com.finova.notification.repository.NotificationPreferenceRepository;
import com.finova.notification.repository.NotificationRepository;
import com.finova.notification.repository.NotificationSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything a customer can do with their own inbox.
 * <p>
 * The owner is a method argument on every single method, never a filter and never
 * something the controller can influence. Ownership is enforced in the query, so
 * a customer can neither read nor mutate a row that is not theirs.
 */
@Service
public class NotificationService {

    public static final int DEFAULT_UNREAD_LIMIT = 20;
    public static final int MAX_UNREAD_LIMIT = 50;
    public static final int MAX_PAGE_SIZE = 100;
    private static final int TREND_DAYS = 7;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationMapper notificationMapper;

    public NotificationService(NotificationRepository notificationRepository,
                               NotificationPreferenceRepository preferenceRepository,
                               NotificationMapper notificationMapper) {
        this.notificationRepository = notificationRepository;
        this.preferenceRepository = preferenceRepository;
        this.notificationMapper = notificationMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(String userId, NotificationFilters filters) {
        return PageResponse.from(findAll(NotificationSpecifications.forUser(userId, filters), filters),
                notificationMapper::toResponse);
    }

    /**
     * Administrator view across every customer, optionally narrowed to one user.
     * Returns the owner-bearing row type; the customer inbox keeps using
     * {@link #list(String, NotificationFilters)} and never sees an owner id.
     * Only reachable from a request that already cleared an ADMIN authorisation.
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminNotificationResponse> adminFeed(String userId, NotificationFilters filters) {
        Specification<Notification> specification = userId == null || userId.isBlank()
                ? NotificationSpecifications.forAdmin(filters)
                : NotificationSpecifications.forUser(userId, filters);
        return PageResponse.from(findAll(specification, filters), notificationMapper::toAdminResponse);
    }

    private Page<Notification> findAll(Specification<Notification> specification, NotificationFilters filters) {
        Pageable pageable = PageRequest.of(
                filters.page(),
                Math.min(filters.size(), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return notificationRepository.findAll(specification, pageable);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> unread(String userId, int limit) {
        int bounded = limit <= 0 ? DEFAULT_UNREAD_LIMIT : Math.min(limit, MAX_UNREAD_LIMIT);
        return notificationRepository
                .findByUserIdAndReadFalseOrderByCreatedAtDesc(userId, PageRequest.of(0, bounded))
                .getContent()
                .stream()
                .map(notificationMapper::toResponse)
                .toList();
    }

    /**
     * Marks one entry read. Idempotent: an entry that is already read comes back
     * unchanged instead of raising an error, because a client retrying after a
     * dropped response is not a mistake.
     */
    @Transactional
    public NotificationResponse markRead(String userId, String id) {
        notificationRepository.markReadIfOwnedAndUnread(id, userId, Instant.now());
        Notification notification = notificationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.NOTIFICATION_NOT_FOUND,
                        "Notification", id));
        return notificationMapper.toResponse(notification);
    }

    @Transactional
    public MarkAllReadResponse markAllRead(String userId) {
        return new MarkAllReadResponse(notificationRepository.markAllReadForUser(userId, Instant.now()));
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(String userId) {
        long unread = notificationRepository.countByUserIdAndReadFalse(userId);
        Map<NotificationCategory, Long> byCategory = new EnumMap<>(NotificationCategory.class);
        for (NotificationCategory category : NotificationCategory.values()) {
            byCategory.put(category, 0L);
        }
        for (NotificationRepository.CategoryCount row : notificationRepository.countUnreadByCategory(userId)) {
            byCategory.put(row.getCategory(), row.getTotal());
        }
        Map<String, Long> wireCategories = new LinkedHashMap<>();
        byCategory.forEach((category, total) -> wireCategories.put(category.value(), total));
        return new UnreadCountResponse(unread, wireCategories);
    }

    @Transactional(readOnly = true)
    public NotificationStatsResponse stats(String userId) {
        Map<String, Long> byType = new LinkedHashMap<>();
        for (NotificationType type : NotificationType.values()) {
            byType.put(type.name(), 0L);
        }
        for (NotificationRepository.TypeCount row : notificationRepository.countAllByType(userId)) {
            byType.put(row.getType().name(), row.getTotal());
        }

        Map<String, Long> byCategory = new LinkedHashMap<>();
        for (NotificationCategory category : NotificationCategory.values()) {
            byCategory.put(category.value(), 0L);
        }
        for (NotificationRepository.CategoryCount row : notificationRepository.countAllByCategory(userId)) {
            byCategory.put(row.getCategory().value(), row.getTotal());
        }

        List<String> days = lastDays();
        Instant since = Instant.now().minus(TREND_DAYS, ChronoUnit.DAYS);
        return new NotificationStatsResponse(
                notificationRepository.countByUserId(userId),
                notificationRepository.countByUserIdAndReadFalse(userId),
                byType,
                byCategory,
                bucket(notificationRepository.findCreatedSince(userId, since), days),
                bucket(notificationRepository.findUnreadCreatedSince(userId, since), days));
    }

    @Transactional(readOnly = true)
    public NotificationResponse get(String userId, String id) {
        return notificationMapper.toResponse(notificationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.NOTIFICATION_NOT_FOUND,
                        "Notification", id)));
    }

    /** Reads the preferences, materialising the default row on first access. */
    @Transactional
    public PreferenceResponse getPreferences(String userId) {
        return notificationMapper.toResponse(preferenceRepository.save(findOrCreatePreference(userId)));
    }

    @Transactional
    public PreferenceResponse updatePreferences(String userId, PreferenceUpdateRequest request) {
        NotificationPreference preference = findOrCreatePreference(userId);
        preference.setEmailEnabled(request.emailEnabled());
        preference.setPushEnabled(request.pushEnabled());
        preference.setInAppEnabled(request.inAppEnabled());
        preference.setTransferAlerts(request.transferAlerts());
        preference.setSecurityAlerts(request.securityAlerts());
        preference.setMarketingEmails(request.marketingEmails());
        preference.setUpdatedAt(Instant.now());
        return notificationMapper.toResponse(preferenceRepository.save(preference));
    }

    private NotificationPreference findOrCreatePreference(String userId) {
        return preferenceRepository.findById(userId)
                .orElseGet(() -> NotificationPreference.defaults(userId, Instant.now()));
    }

    /** The last seven UTC dates, oldest first, so the chart always has seven points. */
    private List<String> lastDays() {
        Instant today = Instant.now().truncatedTo(ChronoUnit.DAYS);
        List<String> days = new ArrayList<>(TREND_DAYS);
        for (int offset = TREND_DAYS - 1; offset >= 0; offset--) {
            days.add(DAY.format(today.minus(offset, ChronoUnit.DAYS).atOffset(ZoneOffset.UTC)));
        }
        return days;
    }

    private List<DailyCount> bucket(List<Instant> timestamps, List<String> days) {
        Map<String, Long> counts = new LinkedHashMap<>();
        days.forEach(day -> counts.put(day, 0L));
        for (Instant timestamp : timestamps) {
            String day = DAY.format(timestamp.atOffset(ZoneOffset.UTC));
            if (counts.containsKey(day)) {
                counts.merge(day, 1L, Long::sum);
            }
        }
        List<DailyCount> result = new ArrayList<>(days.size());
        counts.forEach((day, count) -> result.add(new DailyCount(day, count)));
        return result;
    }
}