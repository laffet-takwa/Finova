package com.finova.user.service;

import com.finova.common.audit.AuditAction;
import com.finova.common.domain.Role;
import com.finova.common.domain.UserStatus;
import com.finova.common.error.BusinessException;
import com.finova.common.error.ErrorCode;
import com.finova.common.security.JwtProperties;
import com.finova.common.security.JwtTokenProvider;
import com.finova.user.domain.RefreshToken;
import com.finova.user.domain.User;
import com.finova.user.dto.AuthResponse;
import com.finova.user.dto.LoginRequest;
import com.finova.user.dto.RegisterRequest;
import com.finova.user.mapper.MappingSupport;
import com.finova.user.mapper.UserMapper;
import com.finova.user.repository.RefreshTokenRepository;
import com.finova.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Registration, sign-in, token rotation and sign-out.
 * <p>
 * Two rules shape almost every method here. First, sign-in must not tell a caller
 * whether an address exists: an unknown address and a wrong password raise the same
 * {@code INVALID_CREDENTIALS} with the same message, and the response time is
 * dominated by the BCrypt comparison in both cases. Second, a refresh token is a
 * <em>session</em>, not a credential: it is stored by its {@code jti}, redeemed once,
 * and rotated, so a captured refresh token buys at most a single exchange.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String RESOURCE_AUTH = "auth";
    private static final String TOKEN_TYPE = "Bearer";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final JwtProperties jwtProperties;
    private final UserMapper userMapper;
    private final AuditRecorder auditRecorder;
    private final RequestContext requestContext;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider,
                       JwtProperties jwtProperties,
                       UserMapper userMapper,
                       AuditRecorder auditRecorder,
                       RequestContext requestContext) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.jwtProperties = jwtProperties;
        this.userMapper = userMapper;
        this.auditRecorder = auditRecorder;
        this.requestContext = requestContext;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = MappingSupport.normaliseEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (request.confirmPassword() != null && !request.confirmPassword().isBlank()
                && !request.confirmPassword().equals(request.password())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "The two passwords do not match.",
                    Map.of("confirmPassword", "must match password"));
        }

        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setEmail(email);
        user.setPhone(MappingSupport.trimToNull(request.phone()));
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(Role.CUSTOMER);
        user.setStatus(UserStatus.ACTIVE);
        User saved = userRepository.save(user);

        auditRecorder.record(AuditAction.USER_REGISTERED, saved.getId(), RESOURCE_AUTH, saved.getId(),
                "SUCCESS", "Customer registered", Map.of("email", saved.getEmail()));
        return issueSession(saved);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = MappingSupport.normaliseEmail(request.email());
        Optional<User> candidate = userRepository.findByEmail(email);
        if (candidate.isEmpty()) {
            // The attempted address is deliberately not written down: it is
            // attacker-supplied, and the audit table is permanent and append-only.
            // The failed attempt is still recorded, without an owner.
            auditRecorder.record(AuditAction.LOGIN_FAILED, null, RESOURCE_AUTH, null,
                    "FAILURE", "Sign-in rejected: unknown account", null);
            throw invalidCredentials();
        }
        User user = candidate.get();
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            auditRecorder.record(AuditAction.LOGIN_FAILED, user.getId(), RESOURCE_AUTH, user.getId(),
                    "FAILURE", "Sign-in rejected: wrong password", null);
            throw invalidCredentials();
        }
        if (user.getStatus() == UserStatus.BLOCKED) {
            auditRecorder.record(AuditAction.LOGIN_FAILED, user.getId(), RESOURCE_AUTH, user.getId(),
                    "FAILURE", "Sign-in rejected: account blocked", null);
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }

        user.setLastLoginAt(Instant.now());
        User saved = userRepository.save(user);
        auditRecorder.record(AuditAction.LOGIN_SUCCESS, saved.getId(), RESOURCE_AUTH, saved.getId(),
                "SUCCESS", "Sign-in succeeded", null);
        return issueSession(saved);
    }

    /**
     * Rotates a refresh token.
     * <p>
     * The presented token must carry {@code typ=refresh}; an access token is not a
     * refresh token, and accepting one would let a short-lived credential mint
     * long-lived sessions. The stored row must exist, be unrevoked and unexpired, and
     * it is revoked in the same transaction that issues its replacement.
     */
    @Transactional
    public AuthResponse refresh(String refreshToken) {
        Claims claims = tokenProvider.parse(refreshToken);
        String tokenType = claims.get(JwtTokenProvider.CLAIM_TOKEN_TYPE, String.class);
        if (!JwtTokenProvider.TYPE_REFRESH.equals(tokenType)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID,
                    ErrorCode.TOKEN_INVALID.defaultMessage());
        }
        String tokenId = claims.get(JwtTokenProvider.CLAIM_TOKEN_ID, String.class);
        RefreshToken stored = tokenId == null ? null
                : refreshTokenRepository.findByTokenId(tokenId).orElse(null);
        if (stored == null || stored.isRevoked() || stored.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID,
                    ErrorCode.TOKEN_INVALID.defaultMessage());
        }
        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID,
                        ErrorCode.TOKEN_INVALID.defaultMessage()));
        if (user.getStatus() == UserStatus.BLOCKED) {
            refreshTokenRepository.revokeAllForUser(user.getId());
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }

        refreshTokenRepository.revokeByTokenId(tokenId);
        auditRecorder.record(AuditAction.TOKEN_REFRESHED, user.getId(), RESOURCE_AUTH, user.getId(),
                "SUCCESS", "Session token rotated", null);
        return issueSession(user);
    }

    /**
     * Revokes the presented session.
     * <p>
     * Always succeeds: an unknown, malformed or absent token still answers 204, so a
     * client can clear local state unconditionally and cannot use the response to
     * probe whether a token exists.
     */
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        try {
            Claims claims = tokenProvider.parse(refreshToken);
            String tokenId = claims.get(JwtTokenProvider.CLAIM_TOKEN_ID, String.class);
            if (tokenId != null) {
                int revoked = refreshTokenRepository.revokeByTokenId(tokenId);
                if (revoked > 0) {
                    String userId = claims.getSubject();
                    auditRecorder.record(AuditAction.LOGOUT, userId, RESOURCE_AUTH, userId,
                            "SUCCESS", "Session ended", null);
                }
            }
        } catch (BusinessException ex) {
            log.debug("Logout presented a token that is already unusable; answering 204 anyway");
        }
    }

    /** Revokes every live session of an identity, used when an administrator blocks it. */
    @Transactional
    public int revokeAllSessions(String userId) {
        return refreshTokenRepository.revokeAllForUser(userId);
    }

    private AuthResponse issueSession(User user) {
        String accessToken = tokenProvider.createAccessToken(user.getId(), user.getEmail(),
                user.getRole().name());
        String refreshToken = tokenProvider.createRefreshToken(user.getId(), user.getEmail(),
                user.getRole().name());
        persistRefreshToken(refreshToken, user);
        return new AuthResponse(accessToken, refreshToken, tokenProvider.expiresInSeconds(),
                TOKEN_TYPE, userMapper.toAuthUser(user));
    }

    private void persistRefreshToken(String refreshToken, User user) {
        Claims claims = tokenProvider.parse(refreshToken);
        RefreshToken stored = new RefreshToken();
        stored.setId(UUID.randomUUID().toString());
        stored.setUserId(user.getId());
        stored.setTokenId(claims.get(JwtTokenProvider.CLAIM_TOKEN_ID, String.class));
        stored.setExpiresAt(Instant.now().plusSeconds(jwtProperties.getRefreshTokenTtlSeconds()));
        stored.setRevoked(false);
        stored.setIpAddress(requestContext.ipAddress());
        refreshTokenRepository.save(stored);
    }

    /**
     * One message for both failure modes. Any wording that distinguished "no such
     * account" from "wrong password" would turn this endpoint into an account
     * existence oracle for anyone who can guess an address.
     */
    private BusinessException invalidCredentials() {
        return new BusinessException(ErrorCode.INVALID_CREDENTIALS,
                ErrorCode.INVALID_CREDENTIALS.defaultMessage());
    }
}
