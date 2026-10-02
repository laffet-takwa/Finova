package com.finova.fraud.controller;

import com.finova.common.error.ApiError;
import com.finova.common.web.PageResponse;
import com.finova.fraud.dto.FraudAlertFilters;
import com.finova.fraud.dto.FraudAlertResponse;
import com.finova.fraud.dto.FraudStatsResponse;
import com.finova.fraud.dto.ReviewAlertRequest;
import com.finova.fraud.service.FraudAlertService;
import com.finova.fraud.service.FraudStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Fraud review console. Every path is administrator-only; the same rule is repeated as a path rule
 * in {@code SecurityConfig} and as {@code @PreAuthorize} so the service stays safe even if one of
 * the two is refactored away.
 */
@RestController
@RequestMapping("/api/fraud")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Fraud", description = "Fraud alerts, administrator review actions and dashboard statistics")
@PreAuthorize("hasRole('ADMIN')")
public class FraudAlertController {

    private final FraudAlertService alertService;
    private final FraudStatsService statsService;

    public FraudAlertController(FraudAlertService alertService, FraudStatsService statsService) {
        this.alertService = alertService;
        this.statsService = statsService;
    }

    @GetMapping("/alerts")
    @Operation(summary = "List fraud alerts", description = "Paginated, optionally filtered by status, risk level, date range or free text.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of fraud alerts"),
            @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Administrator role required", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public PageResponse<FraudAlertResponse> listAlerts(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String search,
            @Parameter(in = ParameterIn.QUERY, description = "Inclusive lower bound, ISO-8601 instant")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(in = ParameterIn.QUERY, description = "Inclusive upper bound, ISO-8601 instant")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return PageResponse.from(alertService.findAll(
                FraudAlertFilters.of(status, riskLevel, search, from, to, page, size)));
    }

    @GetMapping("/alerts/unresolved")
    @Operation(summary = "List unresolved alerts", description = "Alerts still OPEN or UNDER_REVIEW, highest risk first.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Unresolved fraud alerts"),
            @ApiResponse(responseCode = "403", description = "Administrator role required", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public List<FraudAlertResponse> listUnresolved() {
        return alertService.findUnresolved();
    }

    @GetMapping("/alerts/{id}")
    @Operation(summary = "Get one fraud alert")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fraud alert"),
            @ApiResponse(responseCode = "404", description = "Fraud alert not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Administrator role required", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public FraudAlertResponse getAlert(@PathVariable String id) {
        return alertService.findById(id);
    }

    @GetMapping("/alerts/transaction/{transactionId}")
    @Operation(summary = "Get the fraud alert of a transaction")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fraud alert"),
            @ApiResponse(responseCode = "404", description = "No alert was raised for this transaction", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public FraudAlertResponse getAlertByTransaction(@PathVariable String transactionId) {
        return alertService.findByTransactionId(transactionId);
    }

    @PatchMapping("/alerts/{id}/review")
    @Operation(summary = "Start reviewing an alert", description = "Moves the alert to UNDER_REVIEW. No money moves.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated fraud alert"),
            @ApiResponse(responseCode = "404", description = "Fraud alert not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Alert already reviewed", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public FraudAlertResponse startReview(@PathVariable String id,
                                          @Valid @RequestBody(required = false) ReviewAlertRequest request) {
        return alertService.startReview(id, note(request));
    }

    @PatchMapping("/alerts/{id}/safe")
    @Operation(summary = "Mark an alert safe", description = "Releases the held funds by publishing transaction.approved on the outbox.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated fraud alert"),
            @ApiResponse(responseCode = "404", description = "Fraud alert not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Alert already reviewed", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public FraudAlertResponse markSafe(@PathVariable String id,
                                       @Valid @RequestBody(required = false) ReviewAlertRequest request) {
        return alertService.markSafe(id, note(request));
    }

    @PatchMapping("/alerts/{id}/confirm")
    @Operation(summary = "Confirm an alert as fraud", description = "Keeps the funds held for manual recovery. Does not reject the transfer.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated fraud alert"),
            @ApiResponse(responseCode = "404", description = "Fraud alert not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Alert already reviewed", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public FraudAlertResponse confirmFraud(@PathVariable String id,
                                           @Valid @RequestBody(required = false) ReviewAlertRequest request) {
        return alertService.confirmFraud(id, note(request));
    }

    @PatchMapping("/alerts/{id}/block-account")
    @Operation(summary = "Block the sender account and confirm the alert",
            description = "Blocks the sender through the account service, then confirms the alert. "
                    + "If the account service is unavailable the alert is left untouched and 503 is returned.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated fraud alert"),
            @ApiResponse(responseCode = "404", description = "Fraud alert not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Alert already reviewed", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "503", description = "Account service unavailable", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public FraudAlertResponse blockAccount(@PathVariable String id,
                                           @Valid @RequestBody(required = false) ReviewAlertRequest request) {
        return alertService.blockAccount(id, note(request));
    }

    @GetMapping("/stats/summary")
    @Operation(summary = "Fraud dashboard summary", description = "Backlog, risk bands, today's resolutions, 30 day series and the five riskiest sender accounts.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fraud statistics"),
            @ApiResponse(responseCode = "403", description = "Administrator role required", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public FraudStatsResponse stats() {
        return statsService.summary();
    }

    private String note(ReviewAlertRequest request) {
        return request == null ? null : request.noteOrNull();
    }
}
