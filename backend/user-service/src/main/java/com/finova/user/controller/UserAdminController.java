package com.finova.user.controller;

import com.finova.common.domain.Role;
import com.finova.common.domain.UserStatus;
import com.finova.common.security.CurrentUser;
import com.finova.common.web.PageResponse;
import com.finova.user.dto.AdminStatsResponse;
import com.finova.user.dto.AuditLogResponse;
import com.finova.user.dto.UpdateUserStatusRequest;
import com.finova.user.dto.UserSummaryResponse;
import com.finova.user.service.AdminUserService;
import com.finova.user.service.AuditTrailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Cross-customer administration.
 * <p>
 * Every route keeps an {@code admin} path segment, which is what the api-gateway
 * requires an ADMIN token for, and {@code @PreAuthorize} repeats the rule here so a
 * direct call to port 8081 is not a way around it. The gateway does not cover the
 * profile routes, so this service is the only thing standing between an authenticated
 * customer and the full user directory.
 */
@RestController
@RequestMapping("/api/users/admin")
@Tag(name = "Users (admin)", description = "Directory, status switch, statistics and the platform audit trail")
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {

    private final AdminUserService adminUserService;
    private final AuditTrailService auditTrailService;

    public UserAdminController(AdminUserService adminUserService, AuditTrailService auditTrailService) {
        this.adminUserService = adminUserService;
        this.auditTrailService = auditTrailService;
    }

    @GetMapping
    @Operation(summary = "List users",
            description = "Newest first, optionally narrowed by free-text search, role or status.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "One page of identities",
                    content = @Content(schema = @Schema(implementation = PageResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication is required",
                    ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", description = "An ADMIN token is required",
                    ref = "#/components/responses/Forbidden")
    })
    public ResponseEntity<PageResponse<UserSummaryResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        Page<UserSummaryResponse> result = adminUserService.list(search, role, status, page, size);
        return ResponseEntity.ok(PageResponse.from(result));
    }

    @GetMapping("/by-email")
    @Operation(summary = "Resolve an identity by email",
            description = "ADMIN-only on purpose. The account and transaction dev seeders call it to "
                    + "resolve demo user ids, and a customer-reachable email lookup would be an "
                    + "enumeration oracle.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The matching identity",
                    content = @Content(schema = @Schema(implementation = UserSummaryResponse.class))),
            @ApiResponse(responseCode = "403", description = "An ADMIN token is required",
                    ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND",
                    ref = "#/components/responses/NotFound")
    })
    public ResponseEntity<UserSummaryResponse> byEmail(@RequestParam String email) {
        return ResponseEntity.ok(adminUserService.findByEmail(email));
    }

    @GetMapping("/stats")
    @Operation(summary = "User statistics",
            description = "Counts plus a 30-day sign-up series that includes the empty days, so the "
                    + "chart's time axis stays linear.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Directory statistics",
                    content = @Content(schema = @Schema(implementation = AdminStatsResponse.class))),
            @ApiResponse(responseCode = "403", description = "An ADMIN token is required",
                    ref = "#/components/responses/Forbidden")
    })
    public ResponseEntity<AdminStatsResponse> stats() {
        return ResponseEntity.ok(adminUserService.stats());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Read one identity")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The identity",
                    content = @Content(schema = @Schema(implementation = UserSummaryResponse.class))),
            @ApiResponse(responseCode = "403", description = "An ADMIN token is required",
                    ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND",
                    ref = "#/components/responses/NotFound")
    })
    public ResponseEntity<UserSummaryResponse> get(@PathVariable String id) {
        return ResponseEntity.ok(adminUserService.get(id));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Block or unblock an identity",
            description = "Blocking revokes every live session. Repeating the current status is a "
                    + "no-op that answers 200 with the unchanged identity.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The updated identity",
                    content = @Content(schema = @Schema(implementation = UserSummaryResponse.class))),
            @ApiResponse(responseCode = "403", description = "An ADMIN token is required",
                    ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND",
                    ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "405", description = "OPERATION_NOT_ALLOWED",
                    content = @Content(schema = @Schema(implementation = com.finova.common.error.ApiError.class)))
    })
    public ResponseEntity<UserSummaryResponse> updateStatus(@PathVariable String id,
                                                            @Valid @RequestBody UpdateUserStatusRequest request) {
        return ResponseEntity.ok(adminUserService.updateStatus(CurrentUser.userId(), id, request));
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "Search the audit trail",
            description = "Every service's audit events in one timeline, newest first. Filterable by "
                    + "actor, action, result, free text and time window.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "One page of audit rows",
                    content = @Content(schema = @Schema(implementation = PageResponse.class))),
            @ApiResponse(responseCode = "403", description = "An ADMIN token is required",
                    ref = "#/components/responses/Forbidden")
    })
    public ResponseEntity<PageResponse<AuditLogResponse>> auditLogs(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        Page<AuditLogResponse> rows = auditTrailService.search(userId, action, result, search,
                from, to, page, size);
        return ResponseEntity.ok(PageResponse.from(rows));
    }
}
