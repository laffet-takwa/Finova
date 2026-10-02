package com.finova.transaction.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Read-only view of the account product owned by the account-service.
 * <p>
 * The transaction service never asks this service to move money; it only needs
 * the product facts (id, number, holder, type, currency, status) plus, at
 * projection creation time, the opening balance. Every call is wrapped by
 * {@code LedgerProjectionService}, which maps transport failures to
 * {@code SERVICE_UNAVAILABLE} so a directory outage never looks like a missing
 * account.
 */
@FeignClient(name = "account-service", path = "/api/accounts")
public interface AccountServiceClient {

    /**
     * {@code GET /api/accounts/lookup?accountNumber=} - beneficiary lookup used to
     * confirm a receiver exists in the directory without exposing full numbers.
     */
    @GetMapping("/lookup")
    AccountLookupResponse lookupBeneficiary(@RequestParam("accountNumber") String accountNumber);

    /** {@code GET /api/accounts/{id}} - full account record, used to build the ledger projection. */
    @GetMapping("/{id}")
    AccountResponse getAccount(@PathVariable("id") String id);

    /**
     * {@code GET /api/accounts?accountNumber=} - the only account-service endpoint
     * that returns both the account id and the account number, so it is what
     * turns a customer-typed account number into a resolvable ledger row.
     */
    @GetMapping
    List<AccountResponse> findAccounts(@RequestParam("accountNumber") String accountNumber);

    /** {@code GET /api/accounts?email=} - used by the dev seeder to resolve demo holders. */
    @GetMapping
    List<AccountResponse> findAccountsByEmail(@RequestParam("email") String email);
}