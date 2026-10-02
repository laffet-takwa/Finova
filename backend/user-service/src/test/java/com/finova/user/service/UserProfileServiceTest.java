package com.finova.user.service;

import com.finova.common.domain.Role;
import com.finova.common.domain.UserStatus;
import com.finova.common.error.BusinessException;
import com.finova.common.error.ErrorCode;
import com.finova.user.domain.AuditLog;
import com.finova.user.domain.User;
import com.finova.user.dto.AdminStatsResponse;
import com.finova.user.dto.AuthUserResponse;
import com.finova.user.dto.ChangePasswordRequest;
import com.finova.user.dto.SecurityStatusResponse;
import com.finova.user.dto.UpdateProfileRequest;
import com.finova.user.dto.UpdateUserStatusRequest;
import com.finova.user.dto.UserSummaryResponse;
import com.finova.user.event.EventPublisher;
import com.finova.user.mapper.UserMapperImpl;
import com.finova.user.repository.AuditLogRepository;
import com.finova.user.repository.RefreshTokenRepository;
import com.finova.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Profile, security-status and administration rules.
 * <p>
 * The point of interest is what the responses do <em>not</em> contain: a hash in a
 * profile body, a fabricated location, a second-factor flag that claims more than
 * the service can actually do.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserProfileServiceTest {

    private static final String USER_ID = "3f6d9a1c-4b7e-4f0a-9c2d-8e5f1a2b3c4d";
    private static final String ADMIN_ID = "b21f77aa-90c3-4e51-8a77-1c0e5d3b9a20";
    private static final String PASSWORD = "Maroc#2026";

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private EventPublisher eventPublisher;
    @Mock
    private RequestContext requestContext;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private UserProfileService profileService;
    private AdminUserService adminUserService;

    @BeforeEach
    void setUp() {
        AuditRecorder recorder = new AuditRecorder(auditLogRepository, eventPublisher, requestContext);
        profileService = new UserProfileService(userRepository, refreshTokenRepository,
                auditLogRepository, passwordEncoder, new UserMapperImpl(), recorder, 5);
        adminUserService = new AdminUserService(userRepository, auditLogRepository,
                refreshTokenRepository, new UserMapperImpl(), recorder);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(requestContext.ipAddress()).thenReturn("41.226.10.37");
        when(requestContext.correlationId()).thenReturn("test-correlation-id");
    }

    @Test
    @DisplayName("The profile response carries no credential material at all")
    void shouldReturnAProfileWithoutCredentialMaterial() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));

        String body = profileService.currentProfile(USER_ID).toString();

        assertThat(body).doesNotContain(PASSWORD).doesNotContain("$2a$").doesNotContain("passwordHash");
        assertThat(body).contains("takwa@finova.dev").contains("CUSTOMER").contains("ACTIVE");
    }

    @Test
    @DisplayName("Editing the profile trims the names and never touches the email or role")
    void shouldTrimNamesOnProfileUpdate() {
        User stored = user();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(stored));

        profileService.updateProfile(USER_ID,
                new UpdateProfileRequest("  Takwa  ", " Ferchichi ", "  ", null));

        assertThat(stored.getFirstName()).isEqualTo("Takwa");
        assertThat(stored.getLastName()).isEqualTo("Ferchichi");
        assertThat(stored.getPhone()).isNull();
        assertThat(stored.getEmail()).isEqualTo("takwa@finova.dev");
        assertThat(stored.getRole()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    @DisplayName("Echoing the current address back is accepted")
    void shouldAcceptTheUnchangedEmailOnAProfileUpdate() {
        User stored = user();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(stored));

        AuthUserResponse response = profileService.updateProfile(USER_ID,
                new UpdateProfileRequest("Takwa", "Ferchichi", "+216 55 214 780", "  TAKWA@Finova.DEV "));

        assertThat(response.email()).isEqualTo("takwa@finova.dev");
    }

    @Test
    @DisplayName("A different address of record is refused instead of being silently ignored")
    void shouldRefuseAnEmailChangeOnAProfileUpdate() {
        User stored = user();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> profileService.updateProfile(USER_ID,
                new UpdateProfileRequest("Takwa", "Ferchichi", null, "attacker@example.dev")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        assertThat(stored.getEmail()).isEqualTo("takwa@finova.dev");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("A wrong current password is refused and nothing is changed")
    void shouldRefuseAPasswordChangeWithTheWrongCurrentPassword() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));

        assertThatThrownBy(() -> profileService.changePassword(USER_ID,
                new ChangePasswordRequest("Wrong#Pass9", "Tunisie#2027")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

        verify(refreshTokenRepository, never()).revokeAllForUser(anyString());
    }

    @Test
    @DisplayName("Reusing the same password is refused")
    void shouldRefuseReusingTheCurrentPassword() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));

        assertThatThrownBy(() -> profileService.changePassword(USER_ID,
                new ChangePasswordRequest(PASSWORD, PASSWORD)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("A password change re-hashes and ends every other session")
    void shouldRevokeEverySessionOnAPasswordChange() {
        User stored = user();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(stored));

        profileService.changePassword(USER_ID,
                new ChangePasswordRequest(PASSWORD, "Tunisie#2027"));

        assertThat(stored.getPasswordHash()).isNotEqualTo(user().getPasswordHash());
        assertThat(passwordEncoder.matches("Tunisie#2027", stored.getPasswordHash())).isTrue();
        verify(refreshTokenRepository).revokeAllForUser(USER_ID);
    }

    @Test
    @DisplayName("The security panel claims no second factor and no location")
    void shouldReportNoSecondFactorAndNoLocation() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(auditLogRepository.findByUserIdAndActionInOrderByCreatedAtDesc(anyString(), any(), any()))
                .thenReturn(List.of());

        SecurityStatusResponse status = profileService.securityStatus(USER_ID);

        assertThat(status.passwordProtected()).isTrue();
        assertThat(status.twoFactorEnabled()).isFalse();
        assertThat(status.mfaConfigured()).isFalse();
        assertThat(status.locationSource()).isEqualTo("NOT_PROVIDED_BY_BACKEND");
        assertThat(status.recentLogins()).isEmpty();
    }

    @Test
    @DisplayName("The sign-in history reports the address it saw and nulls the location")
    void shouldListRecentSignInsWithoutResolvingThemToAPlace() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(auditLogRepository.findByUserIdAndActionInOrderByCreatedAtDesc(anyString(), any(), any()))
                .thenReturn(List.of(
                        auditEntry("LOGIN_SUCCESS", "SUCCESS", "41.226.10.37"),
                        auditEntry("LOGIN_FAILED", "FAILURE", "197.0.15.9")));

        SecurityStatusResponse status = profileService.securityStatus(USER_ID);

        assertThat(status.recentLogins()).hasSize(2);
        assertThat(status.recentLogins()).allSatisfy(entry -> assertThat(entry.location()).isNull());
        assertThat(status.recentLogins().get(0).ipAddress()).isEqualTo("41.226.10.37");
        assertThat(status.recentLogins().get(0).result()).isEqualTo("SUCCESS");
        assertThat(status.recentLogins().get(1).result()).isEqualTo("FAILURE");
    }

    @Test
    @DisplayName("The email lookup normalises the address exactly as sign-up does")
    void shouldResolveByEmailCaseInsensitively() {
        when(userRepository.findByEmail("takwa@finova.dev")).thenReturn(Optional.of(user()));

        UserSummaryResponse summary = adminUserService.findByEmail("  Takwa@Finova.DEV  ");

        assertThat(summary.id()).isEqualTo(USER_ID);
        assertThat(summary.lastLoginAt()).isNotNull();
    }

    @Test
    @DisplayName("An unknown email answers USER_NOT_FOUND")
    void shouldRefuseAnUnknownEmail() {
        when(userRepository.findByEmail("ghost@finova.dev")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.findByEmail("ghost@finova.dev"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("Blocking a customer flips the status and kills its sessions")
    void shouldRevokeSessionsWhenBlocking() {
        User stored = user();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(stored));

        UserSummaryResponse summary = adminUserService.updateStatus(ADMIN_ID, USER_ID,
                new UpdateUserStatusRequest(UserStatus.BLOCKED, "fraud review"));

        assertThat(summary.status()).isEqualTo(UserStatus.BLOCKED);
        verify(refreshTokenRepository).revokeAllForUser(USER_ID);
        verify(auditLogRepository).save(any());
    }

    @Test
    @DisplayName("Repeating the current status is a no-op, not a conflict")
    void shouldBeIdempotentWhenTheStatusIsUnchanged() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));

        UserSummaryResponse summary = adminUserService.updateStatus(ADMIN_ID, USER_ID,
                new UpdateUserStatusRequest(UserStatus.ACTIVE, null));

        assertThat(summary.status()).isEqualTo(UserStatus.ACTIVE);
        verify(refreshTokenRepository, never()).revokeAllForUser(anyString());
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("An administrator account cannot be blocked from this screen")
    void shouldRefuseToBlockAnAdministrator() {
        User admin = user();
        admin.setId(ADMIN_ID);
        admin.setRole(Role.ADMIN);
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> adminUserService.updateStatus(USER_ID, ADMIN_ID,
                new UpdateUserStatusRequest(UserStatus.BLOCKED, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.OPERATION_NOT_ALLOWED);
    }

    @Test
    @DisplayName("The statistics series has one point per day, empty days included")
    void shouldEmitAContinuousGrowthSeries() {
        when(userRepository.count()).thenReturn(48L);
        when(userRepository.countByStatus(UserStatus.ACTIVE)).thenReturn(47L);
        when(userRepository.countByStatus(UserStatus.BLOCKED)).thenReturn(1L);
        when(userRepository.countByCreatedAtAfter(any())).thenReturn(6L);
        when(userRepository.countByRole(any())).thenReturn(1L);
        when(userRepository.findAllCreatedAt()).thenReturn(List.of(
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().minus(29, ChronoUnit.DAYS)));
        when(auditLogRepository.countGroupedByAction()).thenReturn(List.of());

        AdminStatsResponse stats = adminUserService.stats();

        assertThat(stats.growthSeries()).hasSize(30);
        assertThat(stats.growthSeries()).allSatisfy(point -> {
            assertThat(point.label()).matches("\\d{2} [A-Z][a-z]{2}");
            assertThat(point.count()).isNotNegative();
        });
        // Oldest first, so index 0 is 29 days ago and index 29 is today.
        assertThat(stats.growthSeries().get(0).count()).isEqualTo(1L);
        assertThat(stats.growthSeries().get(28).count()).isEqualTo(2L);
        assertThat(stats.growthSeries().get(29).count()).isZero();
        assertThat(stats.usersByRole()).containsKeys("CUSTOMER", "ADMIN");
    }

    @Test
    @DisplayName("The admin directory page is capped so one request cannot ask for everything")
    void shouldClampTheAdminPageSize() {
        when(userRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(org.springframework.data.domain.Page.empty());

        adminUserService.list(null, null, null, 0, 5_000);
        adminUserService.list(null, null, null, 0, 0);
        adminUserService.list(null, null, null, -3, 25);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository, org.mockito.Mockito.times(3))
                .findAll(any(org.springframework.data.jpa.domain.Specification.class), captor.capture());
        assertThat(captor.getAllValues().get(0).getPageSize()).isEqualTo(100);
        assertThat(captor.getAllValues().get(1).getPageSize()).isEqualTo(20);
        assertThat(captor.getAllValues().get(2).getPageSize()).isEqualTo(25);
        assertThat(captor.getAllValues().get(2).getPageNumber()).isZero();
        // Newest first, so the admin list matches the audit feed's ordering.
        assertThat(captor.getAllValues().get(0).getSort().getOrderFor("createdAt").isDescending())
                .isTrue();
    }

    private User user() {
        User user = new User();
        user.setId(USER_ID);
        user.setFirstName("Takwa");
        user.setLastName("Ferchichi");
        user.setEmail("takwa@finova.dev");
        user.setPhone("+216 55 214 780");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(Role.CUSTOMER);
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(Instant.now().minus(120, ChronoUnit.DAYS));
        user.setLastLoginAt(Instant.now().minus(2, ChronoUnit.HOURS));
        return user;
    }

    private AuditLog auditEntry(String action, String result, String ipAddress) {
        AuditLog entry = new AuditLog();
        entry.setId(UUID.randomUUID().toString());
        entry.setAction(action);
        entry.setResult(result);
        entry.setIpAddress(ipAddress);
        entry.setResource("auth");
        entry.setService("user-service");
        entry.setCreatedAt(Instant.now().minus(1, ChronoUnit.HOURS));
        return entry;
    }
}
