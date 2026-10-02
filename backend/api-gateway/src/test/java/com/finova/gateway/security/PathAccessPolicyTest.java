package com.finova.gateway.security;

import com.finova.gateway.config.GatewayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The allow list and the admin list are the platform's authorisation policy in
 * one place, so both are pinned here rather than only through HTTP calls.
 */
class PathAccessPolicyTest {

    private PathAccessPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new PathAccessPolicy(new GatewayProperties());
    }

    @Test
    @DisplayName("the public allow list matches only the documented paths")
    void shouldMatchThePublicAllowList() {
        assertThat(policy.isPublic("/api/auth/login")).isTrue();
        assertThat(policy.isPublic("/api/auth/register")).isTrue();
        assertThat(policy.isPublic("/api/auth/refresh")).isTrue();
        assertThat(policy.isPublic("/actuator/health")).isTrue();
        assertThat(policy.isPublic("/actuator/prometheus")).isTrue();
        assertThat(policy.isPublic("/v3/api-docs")).isTrue();
        assertThat(policy.isPublic("/v3/api-docs/routes")).isTrue();
        assertThat(policy.isPublic("/swagger-ui/index.html")).isTrue();
        assertThat(policy.isPublic("/docs/user-service/v3/api-docs")).isTrue();
    }

    @Test
    @DisplayName("a protected path is not public")
    void shouldNotMatchProtectedPaths() {
        assertThat(policy.isPublic("/api/accounts")).isFalse();
        assertThat(policy.isPublic("/api/users/me")).isFalse();
        assertThat(policy.isPublic("/api/transactions")).isFalse();
        assertThat(policy.isPublic("/api/notifications")).isFalse();
        assertThat(policy.isPublic("/api/auth/logout")).isFalse();
    }

    @Test
    @DisplayName("the admin prefixes cover the whole fraud surface")
    void shouldMatchAdminPrefixes() {
        assertThat(policy.requiresAdmin("/api/fraud")).isTrue();
        assertThat(policy.requiresAdmin("/api/fraud/alerts")).isTrue();
        assertThat(policy.requiresAdmin("/api/users/admin")).isTrue();
        assertThat(policy.requiresAdmin("/api/users/admin/ban")).isTrue();
    }

    @Test
    @DisplayName("an admin path is a prefix match, not a string prefix match")
    void shouldNotMatchPartialSegments() {
        assertThat(policy.requiresAdmin("/api/fraudulent")).isFalse();
        assertThat(policy.requiresAdmin("/api/accounts")).isFalse();
        assertThat(policy.requiresAdmin("/api/users/me")).isFalse();
    }

    @Test
    @DisplayName("any path with an admin segment is administrative")
    void shouldMatchAdminSegments() {
        assertThat(policy.requiresAdmin("/api/accounts/admin")).isTrue();
        assertThat(policy.requiresAdmin("/api/accounts/admin/close")).isTrue();
    }

    @Test
    @DisplayName("the admin segment rule can be switched off")
    void shouldAllowDisablingTheAdminSegmentRule() {
        GatewayProperties properties = new GatewayProperties();
        properties.setAdminRequiredOnAdminSegments(false);
        PathAccessPolicy relaxed = new PathAccessPolicy(properties);

        assertThat(relaxed.requiresAdmin("/api/accounts/admin")).isFalse();
        assertThat(relaxed.requiresAdmin("/api/fraud/alerts")).isTrue();
    }
}
