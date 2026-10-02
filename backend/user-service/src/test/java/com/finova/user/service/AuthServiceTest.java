package com.finova.user.service;

import com.finova.common.audit.AuditAction;
import com.finova.common.domain.Role;
import com.finova.common.domain.UserStatus;
import com.finova.common.error.BusinessException;
import com.finova.common.error.ErrorCode;
import com.finova.common.security.AuthenticatedUser;
import com.finova.common.security.JwtAuthenticationFilter;
import com.finova.common.security.JwtProperties;
import com.finova.common.security.JwtTokenProvider;
import com.finova.user.domain.RefreshToken;
import com.finova.user.domain.User;
import com.finova.user.dto.AuthResponse;
import com.finova.user.dto.AuthUserResponse;
import com.finova.user.dto.LoginRequest;
import com.finova.user.dto.RefreshTokenRequest;
import com.finova.user.dto.RegisterRequest;
import com.finova.user.event.EventPublisher;
import com.finova.user.mapper.UserMapperImpl;
import com.finova.user.repository.AuditLogRepository;
import com.finova.user.repository.RefreshTokenRepository;
import com.finova.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The authentication rules, which are the part of this service that cannot be wrong.
 * <p>
 * The real BCrypt encoder and the real {@link JwtTokenProvider} are used on purpose:
 * a mock password encoder would happily accept any string and prove nothing, and a
 * mock token provider would let an access token through the refresh path.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    private static final String SECRET = "finova-test-secret-key-that-is-long-enough-32b";
    private static final String STRONG_PASSWORD = "Maroc#2026";

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private EventPublisher eventPublisher;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtProperties jwtProperties = new JwtProperties();

    private AuthService authService;

    @BeforeEach
    void setUp() {
        jwtProperties.setSecret(SECRET);
        jwtProperties.setIssuer("finova");
        jwtProperties.setAccessTokenTtlSeconds(3600L);
        jwtProperties.setRefreshTokenTtlSeconds(604800L);

        authService = new AuthService(userRepository, refreshTokenRepository, passwordEncoder,
                new JwtTokenProvider(jwtProperties), jwtProperties, new UserMapperImpl(),
                new AuditRecorder(auditLogRepository, eventPublisher, fixedContext()),
                fixedContext());
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Registration stores a BCrypt hash, signs the customer in and records the event")
    void shouldRegisterAndIssueASessionWhenTheEmailIsFree() {
        when(userRepository.existsByEmail("takwa@finova.dev")).thenReturn(false);

        AuthResponse response = authService.register(new RegisterRequest("Takwa", "Ferchichi",
                "  Takwa@Finova.DEV ", "+216 55 214 780", STRONG_PASSWORD, STRONG_PASSWORD));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        User user = saved.getValue();

        assertThat(user.getEmail()).isEqualTo("takwa@finova.dev");
        assertThat(user.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getPasswordHash()).startsWith("$2a$").isNotEqualTo(STRONG_PASSWORD);
        assertThat(passwordEncoder.matches(STRONG_PASSWORD, user.getPasswordHash())).isTrue();

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600L);
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.user().email()).isEqualTo("takwa@finova.dev");
        verify(auditLogRepository).save(any());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("The password hash never appears in a registration response")
    void shouldNeverEchoThePasswordHashOnRegistration() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);

        AuthResponse response = authService.register(new RegisterRequest("Takwa", "Ferchichi",
                "takwa@finova.dev", null, STRONG_PASSWORD, STRONG_PASSWORD));

        String serialised = response.toString() + response.user();
        assertThat(serialised).doesNotContain("password").doesNotContain("$2a$");
        assertThat(serialised).doesNotContain(STRONG_PASSWORD);
    }

    @Test
    @DisplayName("A second registration of the same address is refused")
    void shouldRejectDuplicateEmailWhenTheAddressIsTaken() {
        when(userRepository.existsByEmail("takwa@finova.dev")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("Takwa", "Ferchichi",
                "takwa@finova.dev", null, STRONG_PASSWORD, STRONG_PASSWORD)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("A correct password signs the customer in and stamps the sign-in time")
    void shouldIssueASessionWhenThePasswordIsCorrect() {
        User user = user("takwa@finova.dev", STRONG_PASSWORD, UserStatus.ACTIVE);
        when(userRepository.findByEmail("takwa@finova.dev")).thenReturn(Optional.of(user));

        AuthResponse response = authService.login(new LoginRequest("TAKWA@finova.dev ", STRONG_PASSWORD));

        assertThat(response.user().id()).isEqualTo(user.getId());
        assertThat(user.getLastLoginAt()).isNotNull();
        assertThat(user.getLastLoginAt()).isBeforeOrEqualTo(Instant.now().plusSeconds(1));
        verify(userRepository).save(user);
        verify(auditLogRepository).save(any());
    }

    @Test
    @DisplayName("A wrong password answers INVALID_CREDENTIALS and records the attempt")
    void shouldRejectWrongPasswordWithInvalidCredentials() {
        when(userRepository.findByEmail("takwa@finova.dev"))
                .thenReturn(Optional.of(user("takwa@finova.dev", STRONG_PASSWORD, UserStatus.ACTIVE)));

        assertThatThrownBy(() -> authService.login(new LoginRequest("takwa@finova.dev", "Wrong#Pass9")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

        verify(auditLogRepository).save(any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("An unknown address answers exactly the same thing as a wrong password")
    void shouldNotRevealWhetherAnAddressExists() {
        when(userRepository.findByEmail("ghost@finova.dev")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("takwa@finova.dev"))
                .thenReturn(Optional.of(user("takwa@finova.dev", STRONG_PASSWORD, UserStatus.ACTIVE)));

        BusinessException unknown = catchBusiness(() ->
                authService.login(new LoginRequest("ghost@finova.dev", STRONG_PASSWORD)));
        BusinessException wrongPassword = catchBusiness(() ->
                authService.login(new LoginRequest("takwa@finova.dev", "Wrong#Pass9")));

        assertThat(unknown.getErrorCode()).isEqualTo(wrongPassword.getErrorCode());
        assertThat(unknown.getMessage()).isEqualTo(wrongPassword.getMessage());
        assertThat(unknown.getMessage()).isEqualTo(ErrorCode.INVALID_CREDENTIALS.defaultMessage());
    }

    @Test
    @DisplayName("A blocked identity is refused with ACCOUNT_LOCKED and audited")
    void shouldRejectBlockedIdentityWithAccountLocked() {
        when(userRepository.findByEmail("sami.mejboud@finova.dev"))
                .thenReturn(Optional.of(user("sami.mejboud@finova.dev", STRONG_PASSWORD, UserStatus.BLOCKED)));

        assertThatThrownBy(() -> authService.login(new LoginRequest("sami.mejboud@finova.dev", STRONG_PASSWORD)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_LOCKED);

        verify(auditLogRepository).save(any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Refreshing rotates the session: the old row is revoked and a new pair is issued")
    void shouldRotateTheSessionOnRefresh() {
        User user = user("takwa@finova.dev", STRONG_PASSWORD, UserStatus.ACTIVE);
        String refreshToken = new JwtTokenProvider(jwtProperties)
                .createRefreshToken(user.getId(), user.getEmail(), "CUSTOMER");
        String tokenId = new JwtTokenProvider(jwtProperties).parse(refreshToken).getId();

        when(refreshTokenRepository.findByTokenId(tokenId))
                .thenReturn(Optional.of(storedSession(user.getId(), tokenId, false, Instant.now()
                        .plus(7, ChronoUnit.DAYS))));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        AuthResponse response = authService.refresh(new RefreshTokenRequest(refreshToken).refreshToken());

        verify(refreshTokenRepository).revokeByTokenId(tokenId);
        assertThat(response.refreshToken()).isNotBlank().isNotEqualTo(refreshToken);
        ArgumentCaptor<RefreshToken> issued = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(issued.capture());
        assertThat(issued.getValue().getTokenId()).isNotEqualTo(tokenId);
        assertThat(issued.getValue().isRevoked()).isFalse();
    }

    @Test
    @DisplayName("A revoked session cannot be redeemed again")
    void shouldRejectARevokedRefreshToken() {
        User user = user("takwa@finova.dev", STRONG_PASSWORD, UserStatus.ACTIVE);
        JwtTokenProvider provider = new JwtTokenProvider(jwtProperties);
        String refreshToken = provider.createRefreshToken(user.getId(), user.getEmail(), "CUSTOMER");
        String tokenId = provider.parse(refreshToken).getId();
        when(refreshTokenRepository.findByTokenId(tokenId))
                .thenReturn(Optional.of(storedSession(user.getId(), tokenId, true, Instant.now()
                        .plus(7, ChronoUnit.DAYS))));

        assertThatThrownBy(() -> authService.refresh(refreshToken))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TOKEN_INVALID);

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("An expired session is refused")
    void shouldRejectAnExpiredRefreshToken() {
        JwtTokenProvider provider = new JwtTokenProvider(jwtProperties);
        String userId = "3f6d9a1c-4b7e-4f0a-9c2d-8e5f1a2b3c4d";
        String refreshToken = provider.createRefreshToken(userId, "takwa@finova.dev", "CUSTOMER");
        String tokenId = provider.parse(refreshToken).getId();
        when(refreshTokenRepository.findByTokenId(tokenId))
                .thenReturn(Optional.of(storedSession(userId, tokenId, false,
                        Instant.now().minus(1, ChronoUnit.DAYS))));

        assertThatThrownBy(() -> authService.refresh(refreshToken))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TOKEN_INVALID);
    }

    @Test
    @DisplayName("An unknown jti is refused with TOKEN_INVALID")
    void shouldRejectAnUnknownRefreshTokenId() {
        when(refreshTokenRepository.findByTokenId(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(
                new JwtTokenProvider(jwtProperties).createRefreshToken("someone", "a@b.dev", "CUSTOMER")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TOKEN_INVALID);
    }

    @Test
    @DisplayName("An access token is not accepted where a refresh token is required")
    void shouldRejectAnAccessTokenOnTheRefreshPath() {
        JwtTokenProvider provider = new JwtTokenProvider(jwtProperties);
        String accessToken = provider.createAccessToken("someone", "a@b.dev", "CUSTOMER");

        assertThatThrownBy(() -> authService.refresh(accessToken))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TOKEN_INVALID);

        verify(refreshTokenRepository, never()).findByTokenId(anyString());
    }

    @Test
    @DisplayName("A refresh token presented as a bearer credential authenticates nothing")
    void shouldNotAuthenticateWhenARefreshTokenIsUsedAsAnAccessToken() throws Exception {
        JwtTokenProvider provider = new JwtTokenProvider(jwtProperties);
        String refreshToken = provider.createRefreshToken("3f6d9a1c-4b7e-4f0a-9c2d-8e5f1a2b3c4d",
                "takwa@finova.dev", "CUSTOMER");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        request.addHeader("Authorization", "Bearer " + refreshToken);
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            new JwtAuthenticationFilter(provider).doFilter(request, response, (req, res) -> {
            });
            org.springframework.security.core.Authentication authentication =
                    SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication).isNull();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("Logging out revokes the presented session")
    void shouldRevokeTheSessionOnLogout() {
        JwtTokenProvider provider = new JwtTokenProvider(jwtProperties);
        String refreshToken = provider.createRefreshToken("3f6d9a1c-4b7e-4f0a-9c2d-8e5f1a2b3c4d",
                "takwa@finova.dev", "CUSTOMER");
        lenient().when(refreshTokenRepository.revokeByTokenId(anyString())).thenReturn(1);

        authService.logout(refreshToken);

        verify(refreshTokenRepository).revokeByTokenId(provider.parse(refreshToken).getId());
    }

    @Test
    @DisplayName("Logging out with an unusable token is not an error")
    void shouldNotFailWhenTheLogoutTokenIsUnknown() {
        authService.logout("not-a-jwt");
        authService.logout(null);
        authService.logout("   ");

        verify(refreshTokenRepository, never()).revokeByTokenId(anyString());
    }

    private static User user(String email, String rawPassword, UserStatus status) {
        User user = new User();
        user.setId("3f6d9a1c-4b7e-4f0a-9c2d-8e5f1a2b3c4d");
        user.setFirstName("Takwa");
        user.setLastName("Ferchichi");
        user.setEmail(email);
        user.setPasswordHash(new BCryptPasswordEncoder().encode(rawPassword));
        user.setRole(Role.CUSTOMER);
        user.setStatus(status);
        user.setCreatedAt(Instant.now().minus(90, ChronoUnit.DAYS));
        return user;
    }

    private static RefreshToken storedSession(String userId, String tokenId, boolean revoked,
                                              Instant expiresAt) {
        RefreshToken token = new RefreshToken();
        token.setId("b21f77aa-90c3-4e51-8a77-1c0e5d3b9a20");
        token.setUserId(userId);
        token.setTokenId(tokenId);
        token.setRevoked(revoked);
        token.setExpiresAt(expiresAt);
        token.setCreatedAt(Instant.now().minus(1, ChronoUnit.HOURS));
        return token;
    }

    private static RequestContext fixedContext() {
        RequestContext context = org.mockito.Mockito.mock(RequestContext.class);
        lenient().when(context.ipAddress()).thenReturn("41.226.10.37");
        lenient().when(context.correlationId()).thenReturn("test-correlation-id");
        return context;
    }

    private static BusinessException catchBusiness(Runnable action) {
        try {
            action.run();
        } catch (BusinessException ex) {
            return ex;
        }
        throw new AssertionError("Expected a BusinessException");
    }
}
