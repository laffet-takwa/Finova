package com.finova.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finova.common.domain.NotificationType;
import com.finova.common.error.BusinessException;
import com.finova.common.error.ErrorCode;
import com.finova.common.security.AuthenticatedUser;
import com.finova.common.web.PageResponse;
import com.finova.notification.config.SecurityConfig;
import com.finova.notification.domain.NotificationCategory;
import com.finova.notification.domain.NotificationSeverity;
import com.finova.notification.dto.MarkAllReadResponse;
import com.finova.notification.dto.NotificationFilters;
import com.finova.notification.dto.NotificationResponse;
import com.finova.notification.dto.UnreadCountResponse;
import com.finova.notification.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The web contract of the inbox: authentication, the error envelope, and the fact
 * that a caller cannot reach another customer's notification.
 */
@WebMvcTest(controllers = {NotificationController.class, NotificationAdminController.class})
@Import(SecurityConfig.class)
class NotificationControllerTest {

    private static final String CALLER = "demo-takwa";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private NotificationService notificationService;

    @Test
    @DisplayName("An unauthenticated request is refused before it reaches the controller")
    void shouldRejectUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.status").value(401));

        verify(notificationService, never()).list(anyString(), any());
    }

    @Test
    @DisplayName("An authenticated caller lists their own inbox")
    void shouldListNotificationsForAuthenticatedCaller() throws Exception {
        when(notificationService.list(eq(CALLER), any(NotificationFilters.class)))
                .thenReturn(emptyPage());

        mockMvc.perform(get("/api/notifications").with(customer(CALLER)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("A userId query parameter cannot redirect the query to another inbox")
    void shouldIgnoreUserIdQueryParameter() throws Exception {
        when(notificationService.list(eq(CALLER), any(NotificationFilters.class)))
                .thenReturn(emptyPage());

        mockMvc.perform(get("/api/notifications").param("userId", "demo-ines").with(customer(CALLER)))
                .andExpect(status().isOk());

        verify(notificationService).list(eq(CALLER), any(NotificationFilters.class));
    }

    @Test
    @DisplayName("Unread entries are returned without naming their owner")
    void shouldReturnUnreadNotifications() throws Exception {
        when(notificationService.unread(CALLER, 20)).thenReturn(List.of(notification("n1")));

        mockMvc.perform(get("/api/notifications/unread").with(customer(CALLER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("n1"))
                .andExpect(jsonPath("$[0].title").value("Transfer completed"))
                .andExpect(jsonPath("$[0].category").value("TRANSACTIONS"))
                .andExpect(jsonPath("$[0].userId").doesNotExist());
    }

    @Test
    @DisplayName("The unread counter returns all three folders")
    void shouldReturnUnreadCount() throws Exception {
        when(notificationService.unreadCount(CALLER)).thenReturn(new UnreadCountResponse(6L,
                Map.of("TRANSACTIONS", 4L, "SECURITY", 2L, "SYSTEM", 0L)));

        mockMvc.perform(get("/api/notifications/unread-count").with(customer(CALLER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(6))
                .andExpect(jsonPath("$.byCategory.SECURITY").value(2));
    }

    @Test
    @DisplayName("Marking another customer's notification read returns the 404 envelope")
    void shouldReturnNotFoundWhenMarkingAnotherUsersNotificationRead() throws Exception {
        when(notificationService.markRead(CALLER, "n-other"))
                .thenThrow(BusinessException.notFound(ErrorCode.NOTIFICATION_NOT_FOUND,
                        "Notification", "n-other"));

        mockMvc.perform(patch("/api/notifications/n-other/read").with(customer(CALLER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/api/notifications/n-other/read"));
    }

    @Test
    @DisplayName("Marking a notification read returns the updated entry")
    void shouldMarkNotificationRead() throws Exception {
        when(notificationService.markRead(CALLER, "n1")).thenReturn(notification("n1"));

        mockMvc.perform(patch("/api/notifications/n1/read").with(customer(CALLER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("n1"))
                .andExpect(jsonPath("$.read").value(true));
    }

    @Test
    @DisplayName("Mark all read returns the number of entries updated")
    void shouldMarkAllRead() throws Exception {
        when(notificationService.markAllRead(CALLER)).thenReturn(new MarkAllReadResponse(7));

        mockMvc.perform(patch("/api/notifications/read-all").with(customer(CALLER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(7));
    }

    @Test
    @DisplayName("A customer token is refused on the admin feed")
    void shouldForbidCustomerOnAdminFeed() throws Exception {
        mockMvc.perform(get("/api/notifications/admin/feed").with(customer(CALLER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verify(notificationService, never()).adminFeed(any(), any());
    }

    @Test
    @DisplayName("An admin token reaches the platform feed")
    void shouldAllowAdminOnAdminFeed() throws Exception {
        when(notificationService.adminFeed(any(), any(NotificationFilters.class)))
                .thenReturn(emptyPage());

        mockMvc.perform(get("/api/notifications/admin/feed").with(admin("demo-admin")))
                .andExpect(status().isOk());
    }

    private PageResponse<NotificationResponse> emptyPage() {
        return new PageResponse<>(List.of(), 0, 20, 0, 0, true, true);
    }

    private NotificationResponse notification(String id) {
        return new NotificationResponse(id, NotificationType.TRANSFER_COMPLETED.name(),
                NotificationCategory.TRANSACTIONS.value(), NotificationSeverity.SUCCESS.name(),
                "Transfer completed",
                "Your transfer of 250.000 TND to account •••• 4321 was successful.",
                "tx-1", "TX-20261001-00001", new BigDecimal("250.000"), "TND",
                true, Instant.now(), Instant.now());
    }

private static RequestPostProcessor customer(String userId) {
        return authenticated(userId, "CUSTOMER");
    }

    private static RequestPostProcessor admin(String userId) {
        return authenticated(userId, "ADMIN");
    }

    /**
     * Installs exactly the principal the production {@code JwtAuthenticationFilter}
     * installs: an {@link AuthenticatedUser} carrying the role, with the matching
     * {@code ROLE_*} authority. {@code CurrentUser} reads that principal rather than
     * the token, so this is the same security context a real request produces.
     * <p>
     * The usual {@code jwt()} post-processor is not usable here: it needs
     * {@code org.springframework.security.oauth2.jwt.Jwt}, which lives in
     * {@code spring-security-oauth2-jose}, and this module has no OAuth2 resource
     * server on its classpath.
     */
    private static RequestPostProcessor authenticated(String userId, String role) {
        AuthenticatedUser principal = new AuthenticatedUser(
                userId, userId + "@finova.dev", role, "test-correlation-id");
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(principal.authority()))));
    }
}