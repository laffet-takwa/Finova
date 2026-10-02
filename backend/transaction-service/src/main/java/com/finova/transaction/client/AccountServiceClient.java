package com.finova.transaction.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Read-only view of the account product owned by the account-service.
 * <p>
 * Two calls, two purposes, and nothing else:
 * <ul>
 *   <li>{@link #lookupBeneficiary} resolves a <em>receiver</em>. The directory's
 *       list endpoints are owner-scoped, so they can never resolve someone else's
 *       account for a customer token; the beneficiary lookup is the only endpoint
 *       that answers that question, and it now returns {@code accountId} and
 *       {@code userId} so a single call is enough.</li>
 *   <li>{@link #getAccount} resolves a <em>sender</em> by id. A customer always
 *       owns the account they are spending from, so the owner-scoped read is
 *       correct here, and it is the only shape that carries a balance.</li>
 * </ul>
 * The transaction service never asks the account-service to move money; it only
 * reads product facts. Every call is wrapped by {@code LedgerProjectionService},
 * which maps transport failures to {@code SERVICE_UNAVAILABLE} so a directory
 * outage never looks like a missing account.
 */
@FeignClient(name = "account-service", path = "/api/accounts")
public interface AccountServiceClient {

    /**
     * {@code GET /api/accounts/lookup?accountNumber=} - resolves a beneficiary
     * number to an account id and holder id.
     * <p>
     * The directory answers {@code 400} when the number is the caller's own
     * account, which {@code LedgerProjectionService} deliberately remaps to
     * {@code SENDER_RECEIVER_IDENTICAL} rather than letting it surface as a
     * generic validation error.
     */
    @GetMapping("/lookup")
    AccountLookupResponse lookupBeneficiary(@RequestParam("accountNumber") String accountNumber);

    /**
     * {@code GET /api/accounts/{id}} - full account record for the sender, used to
     * build the ledger projection with its opening balance.
     */
    @GetMapping("/{id}")
    AccountResponse getAccount(@PathVariable("id") String id);
}