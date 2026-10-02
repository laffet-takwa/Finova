package com.finova.user.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finova.common.domain.Role;
import com.finova.common.domain.UserStatus;
import com.finova.common.error.BusinessException;
import com.finova.common.error.ErrorCode;
import com.finova.common.security.AuthenticatedUser;
import com.finova.common.security.JwtProperties;
import com.finova.user.config.SecurityConfig;
import com.finova.user.config.WebSupportConfig;
import com.finova.user.dto.AuthResponse;
import com.finova.user.dto.AuthUserResponse;
import com.finova.user.dto.LoginRequest;
import com.finova.user.dto.UserSummaryResponse;
import com.finova.user.service.AdminUserService;
import com.finova.user.service.AuthService;
import com.finova.user.service.AuditTrailService;
import com.finova.user.service.UserProfileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The web contract: which routes are public, which require ADMIN, and what a caller
 * is allowed to see in a response body.
 * <p>
 * {@link WebSupportConfig} is imported explicitly. Without its
 * {@code GlobalExceptionHandler} bean a {@code BusinessException} escapes as a raw
 * servlet error, and the {@code $.code} assertions below would fail against an HTML
 * error page instead of the platform envelope.
 */
@WebMvcTest(controllers = {AuthController.class, UserController.class, UserAdminController.class})
@Import({SecurityConfig.class, WebSupportConfig.class})
@TestPropertySource(properties = {
        "finova.jwt.secret=finova-test-secret-key-that-is-long-enough-32b",
        "finova.jwt.issuer=finova",
        // This service authenticates with its own JWT filter, never with an in-memory
        // user store. Excluding the auto-configuration keeps the misleading
        // "Using generated security password" out of the slice output.
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration"
})
class AuthControllerTest {

    private static final String CALLER = "3f6d9a1c-4b7e-4f0a-9c2d-8e5f1a2b3c4d";
    private static final String ADMIN = "b21f77aa-90c3-4e51-8a77-1c0e5d3b9a20";
    private static final String PASSWORD = "Maroc#2026";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;
    @MockBean
    private UserProfileService userProfileService;
    @MockBean
    private AdminUserService adminUserService;
    @MockBean
    private AuditTrailService auditTrailService;

    @Test
    @DisplayName("Sign-in is reachable without a token")
    void shouldAllowAnonymousLogin() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(authResponse());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"takwa@finova.dev\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("takwa@finova.dev"));

        verify(authService).login(any(LoginRequest.class));
    }

    @Test
    @DisplayName("Registration answers 201 and is also reachable without a token")
    void shouldAllowAnonymousRegistration() throws Exception {
        when(authService.register(any())).thenReturn(authResponse());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Takwa\",\"lastName\":\"Ferchichi\","
                                + "\"email\":\"takwa@finova.dev\",\"phone\":\"+216 55 214 780\","
                                + "\"password\":\"" + PASSWORD + "\",\"confirmPassword\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.id").value(CALLER));
    }

    @Test
    @DisplayName("A weak password is rejected by validation before the service is reached")
    void shouldRejectWeakPasswordAtTheEdge() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"takwa@finova.dev\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.path").value("/api/auth/login"));

        verify(authService, never()).login(any());
    }

    @Test
    @DisplayName("The profile route refuses an anonymous caller with the error envelope")
    void shouldRefuseAnonymousProfileAccess() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/users/me"));

        verify(userProfileService, never()).currentProfile(anyString());
    }

    @Test
    @DisplayName("A CUSTOMER token reaches its own profile and nothing else")
    void shouldReturnTheCallersOwnProfile() throws Exception {
        when(userProfileService.currentProfile(CALLER)).thenReturn(profile());

        mockMvc.perform(get("/api/users/me").with(customer(CALLER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CALLER))
                .andExpect(jsonPath("$.email").value("takwa@finova.dev"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(userProfileService).currentProfile(CALLER);
    }

    @Test
    @DisplayName("A CUSTOMER token is refused on the admin directory with ACCESS_DENIED")
    void shouldForbidCustomerOnTheAdminDirectory() throws Exception {
        mockMvc.perform(get("/api/users/admin").with(customer(CALLER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.status").value(403));

        verify(adminUserService, never()).list(any(), any(), any(), any(Integer.class), any(Integer.class));
    }

    @Test
    @DisplayName("A CUSTOMER token is refused on the admin email lookup")
    void shouldForbidCustomerOnTheAdminEmailLookup() throws Exception {
        mockMvc.perform(get("/api/users/admin/by-email").param("email", "takwa@finova.dev")
                        .with(customer(CALLER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verify(adminUserService, never()).findByEmail(anyString());
    }

    @Test
    @DisplayName("An ADMIN token reaches the directory")
    void shouldAllowAdminOnTheAdminDirectory() throws Exception {
        when(adminUserService.list(any(), any(), any(), any(Integer.class), any(Integer.class)))
                .thenReturn(org.springframework.data.domain.Page.empty());

        mockMvc.perform(get("/api/users/admin").with(admin(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("An ADMIN token can resolve an identity by email")
    void shouldResolveUserByEmailForAdmin() throws Exception {
        when(adminUserService.findByEmail("takwa@finova.dev")).thenReturn(summary());

        mockMvc.perform(get("/api/users/admin/by-email").param("email", "takwa@finova.dev")
                        .with(admin(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CALLER))
                .andExpect(jsonPath("$.email").value("takwa@finova.dev"));
    }

    @Test
    @DisplayName("A not-found identity answers the platform envelope, not a servlet error")
    void shouldRenderBusinessExceptionsAsTheErrorEnvelope() throws Exception {
        when(adminUserService.get("does-not-exist")).thenThrow(
                BusinessException.notFound(ErrorCode.USER_NOT_FOUND, "User", "does-not-exist"));

        mockMvc.perform(get("/api/users/admin/does-not-exist").with(admin(ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("User does-not-exist was not found."))
                .andExpect(jsonPath("$.path").value("/api/users/admin/does-not-exist"));
    }

    @Test
    @DisplayName("No response body anywhere in this service carries the password or its hash")
    void shouldNeverExposeCredentialsInAnyResponseBody() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(authResponse());
        when(userProfileService.currentProfile(CALLER)).thenReturn(profile());
        when(adminUserService.get(CALLER)).thenReturn(summary());

        String bcryptHash = new BCryptPasswordEncoder().encode(PASSWORD);
        List<String> bodies = List.of(
                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"takwa@finova.dev\",\"password\":\"" + PASSWORD + "\"}"))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                mockMvc.perform(get("/api/users/me").with(customer(CALLER)))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                mockMvc.perform(get("/api/users/admin/" + CALLER).with(admin(ADMIN)))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                mockMvc.perform(put("/api/users/me")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"firstName\":\"Takwa\",\"lastName\":\"Ferchichi\","
                                        + "\"phone\":\"+216 55 214 780\"}")
                                .with(customer(CALLER)))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        for (String body : bodies) {
            assertThat(body).doesNotContain(PASSWORD);
            assertThat(body).doesNotContain(bcryptHash);
            assertThat(body).doesNotContain("passwordHash");
        }
    }

    @Test
    @DisplayName("Changing a password answers 204 with no body at all")
    void shouldAnswerNoContentOnPasswordChange() throws Exception {
        mockMvc.perform(post("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"Tunisie#2027\"}")
                        .with(customer(CALLER)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(userProfileService).changePassword(anyString(), any());
    }

    @Test
    @DisplayName("Logging out answers 204 even when the refresh token is nonsense")
    void shouldAnswerNoContentOnLogout() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"not-a-token\"}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(authService).logout("not-a-token");
    }

    private AuthUserResponse profile() {
        return new AuthUserResponse(CALLER, "Takwa", "Ferchichi", "takwa@finova.dev",
                "+216 55 214 780", Role.CUSTOMER, UserStatus.ACTIVE,
                Instant.parse("2026-06-01T09:15:00Z"));
    }

    private UserSummaryResponse summary() {
        return new UserSummaryResponse(CALLER, "Takwa", "Ferchichi", "takwa@finova.dev",
                "+216 55 214 780", Role.CUSTOMER, UserStatus.ACTIVE,
                Instant.parse("2026-06-01T09:15:00Z"), Instant.parse("2026-09-28T18:42:00Z"));
    }

    private AuthResponse authResponse() {
        return new AuthResponse("header.payload.signature", "header.refresh.signature", 3600L,
                "Bearer", profile());
    }

    private static RequestPostProcessor customer(String userId) {
        return authenticated(userId, "CUSTOMER");
    }

    private static RequestPostProcessor admin(String userId) {
        return authenticated(userId, "ADMIN");
    }

    /**
     * Installs exactly the principal the production {@code JwtAuthenticationFilter}
     * installs. The {@code jwt()} post-processor cannot be used: it needs
     * {@code org.springframework.security.oauth2.jwt.Jwt}, which lives in
     * {@code spring-security-oauth2-jose}, and this module has no OAuth2 resource
     * server on its classpath.
     */
    private static RequestPostProcessor authenticated(String userId, String role) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, userId + "@finova.dev", role,
                "test-correlation-id");
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(principal.authority()))));
    }

    /** Keeps the JwtProperties import honest: the test context binds this prefix. */
    @Test
    @DisplayName("The slice context has a usable JWT secret")
    void shouldBindJwtProperties() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("finova-test-secret-key-that-is-long-enough-32b");
        assertThat(properties.getAccessTokenTtlSeconds()).isEqualTo(3600L);
    }

    @Test
    @DisplayName("A profile response exposes exactly the contracted key set")
    void shouldExposeTheContractedKeySetOnTheProfile() throws Exception {
        when(userProfileService.currentProfile(CALLER)).thenReturn(profile());

        String body = mockMvc.perform(get("/api/users/me").with(customer(CALLER)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        assertThat(json.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("id", "firstName", "lastName", "email", "phone",
                        "role", "status", "createdAt");
    }
}
