package com.finova.transaction.controller;

import com.finova.common.domain.Currency;
import com.finova.common.domain.TransactionStatus;
import com.finova.common.domain.TransactionType;
import com.finova.common.security.CurrentUser;
import com.finova.common.web.PageResponse;
import com.finova.transaction.domain.Transaction;
import com.finova.transaction.dto.AdminTransactionStatsResponse;
import com.finova.transaction.dto.TransactionQuery;
import com.finova.transaction.dto.TransactionResponse;
import com.finova.transaction.dto.TransactionSummaryResponse;
import com.finova.transaction.dto.TransactionTimelineResponse;
import com.finova.transaction.dto.TransferOutcome;
import com.finova.transaction.dto.TransferRequest;
import com.finova.transaction.mapper.TransactionMapper;
import com.finova.transaction.service.AdminTransactionStatsService;
import com.finova.transaction.service.TransactionQueryService;
import com.finova.transaction.service.TransactionSummaryService;
import com.finova.transaction.service.TransactionTimelineService;
import com.finova.transaction.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;

@RestController
@RequestMapping("/api/transactions")
@Tag(name = "Transactions", description = "Money movement, history and settlement status")
public class TransactionController {

    public static final String IDEMPOTENCY_REPLAY_HEADER = "Idempotent-Replay";

    private final TransferService transferService;
    private final TransactionQueryService queryService;
    private final TransactionSummaryService summaryService;
    private final TransactionTimelineService timelineService;
    private final AdminTransactionStatsService statsService;
    private final TransactionMapper mapper;

    public TransactionController(TransferService transferService,
                                 TransactionQueryService queryService,
                                 TransactionSummaryService summaryService,
                                 TransactionTimelineService timelineService,
                                 AdminTransactionStatsService statsService,
                                 TransactionMapper mapper) {
        this.transferService = transferService;
        this.queryService = queryService;
        this.summaryService = summaryService;
        this.timelineService = timelineService;
        this.statsService = statsService;
        this.mapper = mapper;
    }

    @PostMapping
    @Operation(summary = "Create a transfer",
            description = "Accepts a transfer as PENDING. The Idempotency-Key header is mandatory: "
                    + "replaying it returns the original transfer with HTTP 200 and Idempotent-Replay: true, "
                    + "while reusing it with a different payload returns 409 IDEMPOTENCY_KEY_REUSED.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Transfer accepted",
                    content = @Content(schema = @Schema(implementation = TransactionResponse.class))),
            @ApiResponse(responseCode = "200", description = "Idempotent replay of an earlier request",
                    content = @Content(schema = @Schema(implementation = TransactionResponse.class))),
            @ApiResponse(responseCode = "409", description = "IDEMPOTENCY_KEY_REUSED", content = @Content),
            @ApiResponse(responseCode = "422", description = "Business rule rejected (balance, amount, currency)",
                    content = @Content)})
    public ResponseEntity<TransactionResponse> create(
            @Parameter(description = "Client generated idempotency key, 8 to 80 characters", required = true,
                    example = "8f14e45f-ea5e-4c3f-9a6f-3b7d2c1e0a94")
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody TransferRequest request,
            HttpServletRequest httpRequest) {
        TransferOutcome outcome = transferService.create(request, idempotencyKey, CurrentUser.userId(),
                CurrentUser.isAdmin(), httpRequest.getRemoteAddr());
        HttpHeaders headers = new HttpHeaders();
        if (outcome.replay()) {
            headers.set(IDEMPOTENCY_REPLAY_HEADER, "true");
            return new ResponseEntity<>(outcome.response(), headers, HttpStatus.OK);
        }
        return ResponseEntity.created(java.net.URI.create("/api/transactions/" + outcome.response().id()))
                .body(outcome.response());
    }

    @GetMapping
    @Operation(summary = "List the caller's transfers",
            description = "Always scoped to the caller's own legs. Administrators may pass userId to widen it.")
    public ResponseEntity<PageResponse<TransactionResponse>> list(
            @Parameter(description = "Free text matched against description and reference")
            @RequestParam(required = false) String search,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) Currency currency,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @Parameter(description = "ISO-8601 instant, inclusive")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @Parameter(description = "Sort, for example createdAt,desc", example = "createdAt,desc")
            @RequestParam(required = false) String sort,
            @Parameter(description = "Administrator only: another holder's transfers")
            @RequestParam(required = false) String userId) {
        TransactionQuery query = new TransactionQuery(search, type, status, accountId, currency, minAmount, maxAmount,
                from, to, page == null ? 0 : page, size == null ? 0 : size, sort);
        Page<Transaction> result = queryService.search(query, CurrentUser.userId(),
                CurrentUser.isAdmin() ? userId : null);
        return ResponseEntity.ok(PageResponse.from(result, mapper::toResponse));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Read one transfer")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transfer found",
                    content = @Content(schema = @Schema(implementation = TransactionResponse.class))),
            @ApiResponse(responseCode = "403", description = "ACCESS_DENIED", content = @Content),
            @ApiResponse(responseCode = "404", description = "TRANSACTION_NOT_FOUND", content = @Content)})
    public ResponseEntity<TransactionResponse> get(@PathVariable String id) {
        Transaction transaction = queryService.requireVisible(id, CurrentUser.userId(), CurrentUser.isAdmin());
        return ResponseEntity.ok(mapper.toResponse(transaction));
    }

    @GetMapping("/{id}/timeline")
    @Operation(summary = "Derive the lifecycle of a transfer",
            description = "Steps and timestamps come from stored columns only; NOTIFIED stays PENDING because the "
                    + "notification service owns that step.")
    public ResponseEntity<TransactionTimelineResponse> timeline(@PathVariable String id) {
        Transaction transaction = queryService.requireVisible(id, CurrentUser.userId(), CurrentUser.isAdmin());
        return ResponseEntity.ok(timelineService.timeline(transaction));
    }

    @GetMapping("/summary")
    @Operation(summary = "Dashboard aggregates for the authenticated customer",
            description = "Income and expenses are computed from the caller's own completed legs. "
                    + "totalBalance is not returned here because the balance belongs to the account service.")
    public ResponseEntity<TransactionSummaryResponse> summary() {
        return ResponseEntity.ok(summaryService.summarise(CurrentUser.userId()));
    }

    @GetMapping("/stats/summary")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Platform-wide transaction statistics", description = "Administrator only.")
    public ResponseEntity<AdminTransactionStatsResponse> stats() {
        return ResponseEntity.ok(statsService.stats());
    }

    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List every transfer", description = "Administrator only. Supports the same filters.")
    public ResponseEntity<PageResponse<TransactionResponse>> adminAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) Currency currency,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        TransactionQuery query = new TransactionQuery(search, type, status, accountId, currency, minAmount, maxAmount,
                from, to, page == null ? 0 : page, size == null ? 0 : size, sort);
        Page<Transaction> result = queryService.search(query, null, userId);
        return ResponseEntity.ok(PageResponse.from(result, mapper::toResponse));
    }
}