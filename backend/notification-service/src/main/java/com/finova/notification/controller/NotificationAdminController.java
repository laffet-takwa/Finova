package com.finova.notification.controller;

import com.finova.common.web.PageResponse;
import com.finova.notification.dto.AdminNotificationResponse;
import com.finova.notification.dto.NotificationFilters;
import com.finova.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Cross-customer view for support and fraud operators.
 * <p>
 * The path keeps an {@code admin} segment, which the gateway already requires an
 * ADMIN token for, and the rule is enforced again here so a direct call to the
 * service port is not enough to get past it.
 */
@RestController
@RequestMapping("/api/notifications/admin")
@Tag(name = "Notifications (admin)", description = "Platform-wide inbox view for administrators")
@PreAuthorize("hasRole('ADMIN')")
public class NotificationAdminController {

    private final NotificationService notificationService;

    public NotificationAdminController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/feed")
    @Operation(summary = "Platform-wide notification feed",
            description = "Newest first across every customer. Rows carry the owner id and a masked "
                    + "owner hint, which is why they use a different type from the customer inbox. "
                    + "Optionally narrowed to one user id.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "One page of activity rows",
                    content = @Content(schema = @Schema(implementation = PageResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication is required", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", description = "An ADMIN token is required", ref = "#/components/responses/Forbidden")
    })
    public ResponseEntity<PageResponse<AdminNotificationResponse>> feed(
            @Parameter(description = "Restrict the feed to one user id. Administrators only.")
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String category,
            @RequestParam(required = false, defaultValue = "false") boolean unreadOnly,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        NotificationFilters filters = new NotificationFilters(
                type, category, unreadOnly, null, from, to, page, size);
        return ResponseEntity.ok(notificationService.adminFeed(userId, filters));
    }
}