package com.finova.transaction.service;

import com.finova.common.error.BusinessException;
import com.finova.common.error.ErrorCode;
import com.finova.common.event.AccountOpenedEvent;
import com.finova.common.support.Money;
import com.finova.transaction.client.AccountLookupResponse;
import com.finova.transaction.client.AccountResponse;
import com.finova.transaction.client.AccountServiceClient;
import com.finova.transaction.domain.LedgerAccount;
import com.finova.transaction.mapper.AccountNumbers;
import com.finova.transaction.repository.LedgerAccountRepository;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Keeps {@code ledger_account} aligned with the account product owned by the
 * account service.
 * <p>
 * A projection is created the first time this service sees an account, either
 * from the {@code account.opened} event or from a Feign lookup, and is only ever
 * refreshed for product fields afterwards - never for the balance, which this
 * service alone owns.
 */
@Service
public class LedgerProjectionService {

    private static final Logger log = LoggerFactory.getLogger(LedgerProjectionService.class);
    private static final String DIRECTORY_UNAVAILABLE =
            "The account directory is temporarily unavailable. Please retry.";

    private final AccountServiceClient accountServiceClient;
    private final LedgerAccountRepository repository;
    private final AccountNumbers accountNumbers;

    public LedgerProjectionService(AccountServiceClient accountServiceClient,
                                  LedgerAccountRepository repository,
                                  AccountNumbers accountNumbers) {
        this.accountServiceClient = accountServiceClient;
        this.repository = repository;
        this.accountNumbers = accountNumbers;
    }

    /** Rule 2: the sender, resolved by account id. */
    @Transactional
    public LedgerAccount resolveByAccountId(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "senderAccountId is required.");
        }
        String id = accountId.trim();
        return repository.findByAccountId(id)
                .orElseGet(() -> project(guard(() -> accountServiceClient.getAccount(id), "getAccount", id)));
    }

    /** Rule 4: the receiver, resolved by the account number the customer typed. */
    @Transactional
    public LedgerAccount resolveByAccountNumber(String accountNumber) {
        String number = accountNumbers.normalise(accountNumber);
        if (number == null || number.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "receiverAccountNumber is required.");
        }
        return repository.findByAccountNumber(number).orElseGet(() -> resolveUnknownNumber(number));
    }

    /**
     * A number the ledger has never seen is first validated against the directory
     * and then resolved to its account id, which is the only shape carrying both
     * the id and the balance needed to open the projection.
     */
    private LedgerAccount resolveUnknownNumber(String number) {
        AccountLookupResponse lookup = guard(
                () -> accountServiceClient.lookupBeneficiary(number), "lookupBeneficiary", number);
        if (lookup == null) {
            throw BusinessException.notFound(ErrorCode.ACCOUNT_NOT_FOUND, "Account", number);
        }
        List<AccountResponse> matches = guard(
                () -> accountServiceClient.findAccounts(number), "findAccounts", number);
        if (matches == null || matches.isEmpty()) {
            throw BusinessException.notFound(ErrorCode.ACCOUNT_NOT_FOUND, "Account", number);
        }
        return project(matches.get(0));
    }

    /** Consumes {@code account.opened} and creates or refreshes the projection. */
    @Transactional
    public LedgerAccount onAccountOpened(AccountOpenedEvent event) {
        AccountResponse account = new AccountResponse(event.accountId(), event.accountNumber(), event.userId(),
                event.accountType(), event.currency(), event.openingBalance(), event.status(), null, null, null);
        if (event.restatement() && event.openingBalance() == null) {
            return repository.findByAccountId(event.accountId())
                    .orElseThrow(() -> BusinessException.notFound(ErrorCode.ACCOUNT_NOT_FOUND,
                            "Account", event.accountId()));
        }
        return project(account);
    }

    /**
     * Creates the projection on first sight, taking the account service balance as
     * the opening balance, and afterwards only mirrors the product fields. The
     * balance column is never written on the update path.
     */
    public LedgerAccount project(AccountResponse account) {
        if (account == null || account.id() == null || account.id().isBlank()) {
            throw BusinessException.notFound(ErrorCode.ACCOUNT_NOT_FOUND, "Account",
                    account == null ? "unknown" : account.id());
        }
        LedgerAccount existing = repository.findByAccountId(account.id()).orElse(null);
        if (existing != null) {
            existing.setAccountNumber(accountNumbers.normalise(account.accountNumber()));
            existing.setUserId(account.userId());
            existing.setAccountType(account.accountType());
            existing.setCurrency(account.currency());
            existing.setStatus(account.status());
            return repository.save(existing);
        }
        LedgerAccount created = new LedgerAccount();
        created.setId(UUID.randomUUID().toString());
        created.setAccountId(account.id());
        created.setAccountNumber(accountNumbers.normalise(account.accountNumber()));
        created.setUserId(account.userId());
        created.setAccountType(account.accountType());
        created.setCurrency(account.currency());
        created.setBalance(Money.scale(account.balance()));
        created.setStatus(account.status());
        return repository.save(created);
    }

    /**
     * There is no resilience4j in this module, so the transport failure mode is
     * mapped by hand: a directory outage is a 503, never a 404, so the caller
     * retries instead of being told their account does not exist.
     */
    private <T> T guard(Supplier<T> call, String operation, String argument) {
        try {
            return call.get();
        } catch (FeignException ex) {
            log.warn("Account directory call {} failed for {}: {}", operation, argument, ex.getMessage());
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, DIRECTORY_UNAVAILABLE);
        }
    }
}