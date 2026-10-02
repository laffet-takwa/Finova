package com.finova.user.service;

import com.finova.common.audit.AuditAction;
import com.finova.common.domain.Role;
import com.finova.common.domain.UserStatus;
import com.finova.common.error.BusinessException;
import com.finova.common.error.ErrorCode;
import com.finova.user.domain.User;
import com.finova.user.dto.AdminStatsResponse;
import com.finova.user.dto.SeriesPointResponse;
import com.finova.user.dto.UpdateUserStatusRequest;
import com.finova.user.dto.UserSummaryResponse;
import com.finova.user.mapper.UserMapper;
import com.finova.user.repository.AuditLogRepository;
import com.finova.user.repository.AuditSpecifications;
import com.finova.user.repository.RefreshTokenRepository;
import com.finova.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Cross-customer administration: the directory, the status switch and the
 * statistics behind the admin dashboard.
 * <p>
 * Reached only from paths that require the ADMIN authority in both the gateway and
 * this service. Nothing here is derived from a caller-supplied id for the caller's own
 * record, so there is no self-service shortcut into another identity.
 */
@Service
public class AdminUserService {

    private static final String RESOURCE_USER = "user";
    private static final int GROWTH_WINDOW_DAYS = 30;
    /**
     * Pinned to English on purpose: these labels are chart axis text, not display
     * strings, and a machine whose default locale is French would otherwise change
     * the axis language for half the deployments.
     */
    private static final DateTimeFormatter DAY_LABEL =
            DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserMapper userMapper;
    private final AuditRecorder auditRecorder;

    public AdminUserService(UserRepository userRepository,
                            AuditLogRepository auditLogRepository,
                            RefreshTokenRepository refreshTokenRepository,
                            UserMapper userMapper,
                            AuditRecorder auditRecorder) {
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userMapper = userMapper;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public Page<UserSummaryResponse> list(String search, Role role, UserStatus status,
                                          int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> result = userRepository.findAll(
                AuditSpecifications.search(search, role, status), pageable);
        return result.map(userMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public UserSummaryResponse get(String userId) {
        return userMapper.toSummary(requireUser(userId));
    }

    /**
     * Resolves an identity by email for the account and transaction dev seeders.
     * <p>
     * The address is normalised exactly as sign-up and sign-in normalise it, which is
     * what makes the lookup exhaustive. The path stays ADMIN-gated on purpose: it is
     * the one place that turns an email address into an identity, and it must not
     * become a customer-facing enumeration endpoint.
     */
    @Transactional(readOnly = true)
    public UserSummaryResponse findByEmail(String email) {
        String normalised = com.finova.user.mapper.MappingSupport.normaliseEmail(email);
        return userRepository.findByEmail(normalised)
                .map(userMapper::toSummary)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.USER_NOT_FOUND,
                        "User", normalised));
    }

    /**
     * Blocks or unblocks an identity.
     * <p>
     * Blocking revokes every live refresh session in the same transaction: a blocked
     * identity must not keep an access token alive to the end of its short TTL just
     * because no one happened to call logout. The change is idempotent — an identity
     * already in the requested state is returned untouched rather than raising a
     * conflict, because an administrator retrying a click should not be punished.
     */
    @Transactional
    public UserSummaryResponse updateStatus(String actorId, String userId, UpdateUserStatusRequest request) {
        User user = requireUser(userId);
        if (user.getStatus() == request.status()) {
            return userMapper.toSummary(user);
        }
        if (user.getRole() == Role.ADMIN && request.status() == UserStatus.BLOCKED) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED,
                    "An administrator account cannot be blocked from this screen.");
        }
        user.setStatus(request.status());
        User saved = userRepository.save(user);
        if (request.status() == UserStatus.BLOCKED) {
            refreshTokenRepository.revokeAllForUser(userId);
        }

        auditRecorder.record(AuditAction.ACCOUNT_STATUS_CHANGED, actorId, RESOURCE_USER, userId,
                "SUCCESS",
                request.status() == UserStatus.BLOCKED ? "Customer blocked by an administrator"
                        : "Customer unblocked by an administrator",
                request.reason() == null || request.reason().isBlank()
                        ? Map.of("status", request.status().name())
                        : Map.of("status", request.status().name(), "reason", request.reason().trim()));
        return userMapper.toSummary(saved);
    }

    @Transactional(readOnly = true)
    public AdminStatsResponse stats() {
        Instant monthStart = Instant.now().minus(30, ChronoUnit.DAYS);
        List<Instant> createdAt = userRepository.findAllCreatedAt();

        Map<String, Long> byRole = new LinkedHashMap<>();
        for (Role role : Role.values()) {
            byRole.put(role.name(), userRepository.countByRole(role));
        }
        Map<String, Long> byAction = new LinkedHashMap<>();
        for (AuditLogRepository.ActionCount count : auditLogRepository.countGroupedByAction()) {
            byAction.put(count.getAction(), count.getTotal());
        }

        return new AdminStatsResponse(
                userRepository.count(),
                userRepository.countByStatus(UserStatus.ACTIVE),
                userRepository.countByStatus(UserStatus.BLOCKED),
                userRepository.countByCreatedAtAfter(monthStart),
                growthSeries(createdAt),
                byRole,
                byAction);
    }

    /**
     * One point per day over the window, including the empty days.
     * <p>
     * Emitting the gaps is the whole point: a sparse list of days that happened to
     * have a sign-up makes a chart whose x axis moves non-linearly, which reads as
     * "activity collapsed" on a day that simply had none.
     */
    private List<SeriesPointResponse> growthSeries(List<Instant> createdAt) {
        Instant today = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Map<Instant, Long> perDay = new LinkedHashMap<>();
        for (int offset = GROWTH_WINDOW_DAYS - 1; offset >= 0; offset--) {
            perDay.put(today.minus(offset, ChronoUnit.DAYS), 0L);
        }
        for (Instant instant : createdAt) {
            perDay.merge(instant.truncatedTo(ChronoUnit.DAYS), 1L, Long::sum);
        }
        List<SeriesPointResponse> series = new ArrayList<>(perDay.size());
        perDay.forEach((day, count) -> series.add(new SeriesPointResponse(DAY_LABEL.format(day), count)));
        return series;
    }

    private int clampSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, 100);
    }

    private User requireUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.USER_NOT_FOUND, "User", userId));
    }
}
