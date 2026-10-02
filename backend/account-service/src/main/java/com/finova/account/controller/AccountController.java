package com.finova.account.controller;

import com.finova.account.dto.AccountListFilter;
import com.finova.account.dto.AccountLookupResponse;
import com.finova.account.dto.AccountResponse;
import com.finova.account.dto.AccountSearchCriteria;
import com.finova.account.dto.AccountStatsResponse;
import com.finova.account.dto.BalanceResponse;
import com.finova.account.dto.CreateAccountRequest;
import com.finova.account.dto.StatusUpdateRequest;
import com.finova.account.mapper.AccountMapper;
import com.finova.account.service.AccountService;
import com.finova.account.service.AccountStatsService;
import com.finova.common.security.CurrentUser;
import com.finova.common.web.PageResponse;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Account product API.
 * <p>
 * Authorisation contract: a CUSTOMER may only read the accounts they hold, plus
 * the masked beneficiary lookup needed before a transfer. Creation and every
 * status change are privileged: an ADMIN may act on any account (and may open an
 * account for another customer by passing {@code userId} in the body), while a
 * CUSTOMER can only open accounts for themselves. Balances are never written
 * through this API: {@code POST /api/accounts} only assigns the opening balance
 * and {@code GET /{id}/balance} reads a projection maintained from
 * {@code transaction.completed}.
 */
@RestController
@RequestMapping("/api/accounts")
@Tag(name = "Accounts", description = "Bank account ownership, balances and status lifecycle")
@SecurityRequirement(name = "bearerAuth")
public class AccountController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String DEFAULT_SORT = "createdAt,desc";

    private final AccountService accountService;
    private final AccountStatsService accountStatsService;
    private final AccountMapper accountMapper;

    public AccountController(AccountService accountService,
                             AccountStatsService accountStatsService,
                             AccountMapper accountMapper) {
        this.accountService = accountService;
        this.accountStatsService = accountStatsService;
        this.accountMapper = accountMapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Open a bank account",
        description = "Assigns the account number, IBAN and opening balance. Only an ADMIN may pass userId in the body.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Account created"),
        @ApiResponse(responseCode = "400", description = "Invalid payload", content = @Content),
        @ApiResponse(responseCode = "409", description = "This customer already holds this account type and currency",
            content = @Content),
        @ApiResponse(responseCode = "422", description = "ACCOUNT_LIMIT_REACHED", content = @Content)
    })
    public AccountResponse create(@Valid @RequestBody CreateAccountRequest request) {
        String callerId = CurrentUser.userId();
        String ownerId = CurrentUser.isAdmin() && request.userId() != null && !request.userId().isBlank()
            ? request.userId()
            : callerId;
        return accountService.create(ownerId, request);
    }

    @GetMapping
    @Operation(summary = "List accounts",
        description = "A CUSTOMER always receives their own accounts and can never widen that scope with a "
            + "filter. An ADMIN may narrow the result with userId or look across customers with accountNumber or email.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Accounts returned, empty when a filter matches none of the "
            + "caller's own accounts"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content)
    })
    public List<AccountResponse> list(
        @Parameter(description = "Owner filter, ADMIN only")
        @RequestParam(required = false) String userId,
        @Parameter(description = "Exact account number, whitespace and case insensitive, so a formatted number and "
            + "its normalised form resolve identically. A CUSTOMER can only match an account they hold themselves.")
        @RequestParam(required = false) String accountNumber,
        @Parameter(description = "Owner email, lowercased and trimmed, resolved through the user-service. "
            + "A CUSTOMER can only match accounts they hold themselves.")
        @RequestParam(required = false) String email) {
        AccountListFilter filter = AccountListFilter.of(userId, accountNumber, email);
        if (CurrentUser.isAdmin()) {
            return accountService.listForAdmin(filter);
        }
        return accountService.listForUser(CurrentUser.userId(), filter);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Read one account")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Account returned"),
        @ApiResponse(responseCode = "403", description = "ACCESS_DENIED", content = @Content),
        @ApiResponse(responseCode = "404", description = "ACCOUNT_NOT_FOUND", content = @Content)
    })
    public AccountResponse get(@PathVariable String id) {
        return accountService.getOwned(CurrentUser.userId(), id);
    }

    @GetMapping("/{id}/balance")
    @Operation(summary = "Read the projected balance",
        description = "Eventually consistent read projection; the authoritative balance lives in the transaction-service.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Balance returned"),
        @ApiResponse(responseCode = "403", description = "ACCESS_DENIED", content = @Content),
        @ApiResponse(responseCode = "404", description = "ACCOUNT_NOT_FOUND", content = @Content)
    })
    public BalanceResponse balance(@PathVariable String id) {
        return accountService.balance(CurrentUser.userId(), id);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Change an account status (ADMIN)",
        description = "ACTIVE to BLOCKED, BLOCKED to ACTIVE, or CLOSED when the balance is zero. CLOSED is terminal.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status changed"),
        @ApiResponse(responseCode = "403", description = "A CUSTOMER may not change an account status", content = @Content),
        @ApiResponse(responseCode = "404", description = "ACCOUNT_NOT_FOUND", content = @Content),
        @ApiResponse(responseCode = "405", description = "OPERATION_NOT_ALLOWED", content = @Content)
    })
    public AccountResponse updateStatus(@PathVariable String id, @Valid @RequestBody StatusUpdateRequest request) {
        return accountService.updateStatus(CurrentUser.userId(), id, request);
    }

    @GetMapping("/lookup")
    @Operation(summary = "Look up a transfer beneficiary",
        description = "Receiver details for a payment the caller is about to make. Discloses the opaque accountId "
            + "and userId the payer needs to address the beneficiary, never the holder name, no balance and no "
            + "contact details. The caller's own account is rejected.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Beneficiary found"),
        @ApiResponse(responseCode = "400", description = "Malformed number, or your own account", content = @Content),
        @ApiResponse(responseCode = "404", description = "ACCOUNT_NOT_FOUND", content = @Content)
    })
    public AccountLookupResponse lookup(
        @Parameter(description = "Account number, formatted or normalised", required = true)
        @RequestParam("accountNumber") String accountNumber) {
        return accountService.lookupBeneficiary(accountNumber);
    }

    @GetMapping("/stats/summary")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Aggregated account statistics (ADMIN)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Statistics returned"),
        @ApiResponse(responseCode = "403", description = "ADMIN only", content = @Content)
    })
    public AccountStatsResponse stats() {
        return accountStatsService.summary();
    }

    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Search every account (ADMIN)",
        description = "Paginated with optional free text, status, type, currency, balance range and creation window.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Page of accounts"),
        @ApiResponse(responseCode = "403", description = "ADMIN only", content = @Content)
    })
    public PageResponse<AccountResponse> adminAll(
        @RequestParam(required = false) String search,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String accountType,
        @RequestParam(required = false) String currency,
        @RequestParam(required = false) String userId,
        @RequestParam(required = false) BigDecimal minBalance,
        @RequestParam(required = false) BigDecimal maxBalance,
        @Parameter(in = ParameterIn.QUERY, description = "ISO-8601 creation lower bound",
            schema = @Schema(type = "string", format = "date-time"))
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @Parameter(in = ParameterIn.QUERY, description = "ISO-8601 creation upper bound",
            schema = @Schema(type = "string", format = "date-time"))
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = DEFAULT_SORT) String sort) {
        AccountSearchCriteria criteria = AccountSearchCriteria.of(search, status, accountType, currency,
            userId, minBalance, maxBalance, from, to);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), safeSize, parseSort(sort));
        return PageResponse.from(accountService.search(criteria, pageRequest), accountMapper::toResponse);
    }

    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        String[] parts = sort.split(",");
        Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())
            ? Sort.Direction.ASC
            : Sort.Direction.DESC;
        return Sort.by(direction, parts[0].trim());
    }
}
