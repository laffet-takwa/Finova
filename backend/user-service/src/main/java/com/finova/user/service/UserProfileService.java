package com.finova.user.service;

import com.finova.common.audit.AuditAction;
import com.finova.common.error.BusinessException;
import com.finova.common.error.ErrorCode;
import com.finova.user.domain.AuditLog;
import com.finova.user.domain.User;
import com.finova.user.dto.AuthUserResponse;
import com.finova.user.dto.ChangePasswordRequest;
import com.finova.user.dto.SecurityLoginEntry;
import com.finova.user.dto.SecurityStatusResponse;
import com.finova.user.dto.UpdateProfileRequest;
import com.finova.user.mapper.MappingSupport;
import com.finova.user.mapper.UserMapper;
import com.finova.user.repository.AuditLogRepository;
import com.finova.user.repository.AuditSpecifications;
import com.finova.user.repository.RefreshTokenRepository;
import com.finova.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The caller's own profile, password and sign-in history.
 * <p>
 * Every method here resolves the identity from the authenticated principal rather
 * than from a request parameter, so there is no route by which one customer can name
 * another in a URL and reach their profile.
 */
@Service
public class UserProfileService {

    private static final String RESOURCE_USER = "user";
    /** How many of the caller's profile audit rows the password-change scan looks at. */
    private static final int PROFILE_AUDIT_SCAN = 20;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final AuditRecorder auditRecorder;
    private final int recentLoginLimit;

    public UserProfileService(UserRepository userRepository,
                              RefreshTokenRepository refreshTokenRepository,
                              AuditLogRepository auditLogRepository,
                              PasswordEncoder passwordEncoder,
                              UserMapper userMapper,
                              AuditRecorder auditRecorder,
                              @Value("${finova.users.recent-login-limit:5}") int recentLoginLimit) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
        this.auditRecorder = auditRecorder;
        this.recentLoginLimit = recentLoginLimit;
    }

    @Transactional(readOnly = true)
    public AuthUserResponse currentProfile(String userId) {
        return userMapper.toAuthUser(requireUser(userId));
    }

    @Transactional
    public AuthUserResponse updateProfile(String userId, UpdateProfileRequest request) {
        User user = requireUser(userId);
        requireEmailUnchanged(user, request.email());
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setPhone(MappingSupport.trimToNull(request.phone()));
        User saved = userRepository.save(user);

        auditRecorder.record(AuditAction.PROFILE_UPDATED, saved.getId(), RESOURCE_USER, saved.getId(),
                "SUCCESS", "Profile updated", null);
        return userMapper.toAuthUser(saved);
    }

    /**
     * Changes the caller's password and ends every other session.
     * <p>
     * The stored hash is the only thing compared, and the new one is never returned,
     * logged or audited. Revoking the remaining sessions is the point: a password
     * change is the usual response to a suspected compromise, and leaving the other
     * refresh tokens alive would leave the intruder exactly where they were.
     */
    @Transactional
    public void changePassword(String userId, ChangePasswordRequest request) {
        User user = requireUser(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS,
                    "The current password is incorrect.");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "The new password must be different from the current one.",
                    Map.of("newPassword", "must differ from the current password"));
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        refreshTokenRepository.revokeAllForUser(userId);

        auditRecorder.record(AuditAction.PROFILE_UPDATED, userId, RESOURCE_USER, userId,
                "SUCCESS", "Password changed; other sessions ended", Map.of("change", "password"));
    }

    /**
     * Assembles the security panel.
     * <p>
     * The sign-in history is read back from the audit trail rather than from a
     * parallel table, so the customer sees the same record an investigator would.
     * Second-factor flags are hard-coded false because no second factor exists in
     * this service, and the address is never resolved to a place.
     */
    @Transactional(readOnly = true)
    public SecurityStatusResponse securityStatus(String userId) {
        requireUser(userId);
        List<AuditLog> events = auditLogRepository.findByUserIdAndActionInOrderByCreatedAtDesc(
                userId, AuditSpecifications.LOGIN_ACTIONS, PageRequest.of(0, recentLoginLimit));

        List<SecurityLoginEntry> recentLogins = events.stream()
                .map(event -> new SecurityLoginEntry(event.getCreatedAt(), event.getIpAddress(),
                        "Unknown device", null, null, event.getResult()))
                .toList();

        return new SecurityStatusResponse(true, false, false,
                lastPasswordChange(userId), SecurityStatusResponse.LOCATION_SOURCE, recentLogins);
    }

    /**
     * The most recent password change, found by scanning the caller's own profile
     * audit rows for the one tagged as a credential change rather than a name or
     * phone edit. Null for an identity that has never changed its password.
     */
    private Instant lastPasswordChange(String userId) {
        return auditLogRepository.findByUserIdAndActionInOrderByCreatedAtDesc(
                        userId, List.of("PROFILE_UPDATED"), PageRequest.of(0, PROFILE_AUDIT_SCAN))
                .stream()
                .filter(event -> event.getMetadata() != null
                        && "password".equals(event.getMetadata().get("change")))
                .map(AuditLog::getCreatedAt)
                .findFirst()
                .orElse(null);
    }

    /**
     * Refuses an attempt to change the address of record here.
     * <p>
     * Rejecting is deliberate and so is comparing rather than trusting: the address
     * is the identity key, it is what the audit trail and every downstream service
     * key on, and moving it needs a verified flow of its own. Echoing the current
     * address back is accepted so a client that always submits the whole form is not
     * punished for sending a field it cannot change.
     */
    private void requireEmailUnchanged(User user, String submittedEmail) {
        if (submittedEmail == null || submittedEmail.isBlank()) {
            return;
        }
        String submitted = MappingSupport.normaliseEmail(submittedEmail);
        if (!submitted.equals(MappingSupport.normaliseEmail(user.getEmail()))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "The email address cannot be changed from this screen.",
                    Map.of("email", "changing the address of record requires a verified flow"));
        }
    }

    private User requireUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.USER_NOT_FOUND, "User", userId));
    }
}
