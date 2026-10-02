package com.finova.notification.controller;

import com.finova.common.security.CurrentUser;
import com.finova.common.web.PageResponse;
import com.finova.notification.dto.MarkAllReadResponse;
import com.finova.notification.dto.NotificationFilters;
import com.finova.notification.dto.NotificationResponse;
import com.finova.notification.dto.NotificationStatsResponse;
import com.finova.notification.dto.PreferenceResponse;
import com.finova.notification.dto.PreferenceUpdateRequest;
import com.finova.notification.dto.UnreadCountResponse;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * The caller's own inbox.
 * <p>
 * No endpoint here accepts a user id: the owner comes from the access token on
 * every call. There is deliberately no {@code @RequestParam("userId")} on any of
 * them, so ownership cannot be widened from the client side.
 */
@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "The authenticated customer's notification inbox")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(summary = "List my notifications",
            description = "Newest first. Filters are optional and are always applied on top of the "
                    + "authenticated user, never instead of it.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "One page of inbox entries",
                    content = @Content(schema = @Schema(implementation = PageResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication is required", ref = "#/components/responses/Unauthorized")
    })
    public ResponseEntity<PageResponse<NotificationResponse>> list(
            @Parameter(description = "NotificationType name, for example TRANSFER_COMPLETED")
            @RequestParam(required = false) String type,
            @Parameter(description = "TRANSACTIONS, SECURITY or SYSTEM")
            @RequestParam(required = false) String category,
            @Parameter(description = "Return only entries the customer has not opened")
            @RequestParam(required = false, defaultValue = "false") boolean unreadOnly,
            @Parameter(description = "Case-insensitive substring of the title or the message")
            @RequestParam(required = false) String search,
            @Parameter(description = "Inclusive lower bound on createdAt, ISO-8601")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "Exclusive upper bound on createdAt, ISO-8601")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        NotificationFilters filters = new NotificationFilters(
                type, category, unreadOnly, search, from, to, page, size);
        return ResponseEntity.ok(notificationService.list(CurrentUser.userId(), filters));
    }

    @GetMapping("/unread")
    @Operation(summary = "List my unread notifications",
            description = "Used by the notification bell. Capped at 50 entries.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Unread entries, newest first"),
            @ApiResponse(responseCode = "401", description = "Authentication is required", ref = "#/components/responses/Unauthorized")
    })
    public ResponseEntity<List<NotificationResponse>> unread(
            @Parameter(description = "Maximum number of entries, 1 to 50")
            @RequestParam(required = false, defaultValue = "20") int limit) {
        return ResponseEntity.ok(notificationService.unread(CurrentUser.userId(), limit));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Unread counters",
            description = "Total unread count plus a per-folder breakdown that always carries all three folders.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Unread counters"),
            @ApiResponse(responseCode = "401", description = "Authentication is required", ref = "#/components/responses/Unauthorized")
    })
    public ResponseEntity<UnreadCountResponse> unreadCount() {
        return ResponseEntity.ok(notificationService.unreadCount(CurrentUser.userId()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Read one notification")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The notification"),
            @ApiResponse(responseCode = "401", description = "Authentication is required", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "404", description = "No such notification belongs to the caller",
                    ref = "#/components/responses/NotFound")
    })
    public ResponseEntity<NotificationResponse> get(@PathVariable String id) {
        return ResponseEntity.ok(notificationService.get(CurrentUser.userId(), id));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark one notification as read",
            description = "Idempotent: an already-read notification is returned unchanged.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The notification after the update"),
            @ApiResponse(responseCode = "401", description = "Authentication is required", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "404", description = "No such notification belongs to the caller",
                    ref = "#/components/responses/NotFound")
    })
    public ResponseEntity<NotificationResponse> markRead(@PathVariable String id) {
        return ResponseEntity.ok(notificationService.markRead(CurrentUser.userId(), id));
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Mark the whole inbox as read")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "How many entries were actually updated"),
            @ApiResponse(responseCode = "401", description = "Authentication is required", ref = "#/components/responses/Unauthorized")
    })
    public ResponseEntity<MarkAllReadResponse> markAllRead() {
        return ResponseEntity.ok(notificationService.markAllRead(CurrentUser.userId()));
    }

    @GetMapping("/stats/summary")
    @Operation(summary = "Inbox statistics",
            description = "Totals, per-type and per-folder breakdowns, and two zero-filled seven day series.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Inbox statistics"),
            @ApiResponse(responseCode = "401", description = "Authentication is required", ref = "#/components/responses/Unauthorized")
    })
    public ResponseEntity<NotificationStatsResponse> stats() {
        return ResponseEntity.ok(notificationService.stats(CurrentUser.userId()));
    }

    @GetMapping("/preferences")
    @Operation(summary = "Read my delivery preferences",
            description = "The default set is materialised on the first call.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The caller's preferences"),
            @ApiResponse(responseCode = "401", description = "Authentication is required", ref = "#/components/responses/Unauthorized")
    })
    public ResponseEntity<PreferenceResponse> preferences() {
        return ResponseEntity.ok(notificationService.getPreferences(CurrentUser.userId()));
    }

    @PutMapping("/preferences")
    @Operation(summary = "Replace my delivery preferences")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The stored preferences"),
            @ApiResponse(responseCode = "401", description = "Authentication is required", ref = "#/components/responses/Unauthorized")
    })
    public ResponseEntity<PreferenceResponse> updatePreferences(
            @RequestBody PreferenceUpdateRequest request) {
        return ResponseEntity.ok(notificationService.updatePreferences(CurrentUser.userId(), request));
    }
}