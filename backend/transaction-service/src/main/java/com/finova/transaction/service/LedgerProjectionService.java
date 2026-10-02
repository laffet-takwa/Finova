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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * Rule 4: the receiver, resolved by the account number the customer typed.
     * <p>
     * The directory's list endpoints are owner-scoped, so for a customer token
     * they can never name someone else's account. The beneficiary lookup is
     * therefore the single source of truth for this leg and is consulted on every
     * transfer, not only when the projection is missing: it is the caller-scoped
     * read, so it also rejects a transfer to the caller's own account.
     */
    @Transactional
    public LedgerAccount resolveByAccountNumber(String accountNumber) {
        String number = accountNumbers.normalise(accountNumber);
        if (number == null || number.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "receiverAccountNumber is required.");
        }
        return projectFromLookup(lookupReceiver(number), number);
    }

    /**
     * Calls the directory and translates its failure modes into the error codes
     * the client contract mandates.
     * <p>
     * The {@code 400} is remapped on purpose: the directory rejects the caller's
     * own account, and "the source and destination accounts must be different" is
     * far more useful to the customer than a generic validation error. A
     * {@code 404} is a genuinely unknown number; anything else - including a
     * connection failure, which Feign reports with status -1 - is an outage.
     */
    private AccountLookupResponse lookupReceiver(String number) {
        AccountLookupResponse lookup;
        try {
            lookup = accountServiceClient.lookupBeneficiary(number);
        } catch (FeignException ex) {
            int status = ex.status();
            log.warn("Account directory lookup failed for {} with status {}: {}", number, status, ex.getMessage());
            if (status == HttpStatus.BAD_REQUEST.value()) {
                throw new BusinessException(ErrorCode.SENDER_RECEIVER_IDENTICAL,
                        ErrorCode.SENDER_RECEIVER_IDENTICAL.defaultMessage());
            }
            if (status == HttpStatus.NOT_FOUND.value()) {
                throw BusinessException.notFound(ErrorCode.ACCOUNT_NOT_FOUND, "Account", number);
            }
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, DIRECTORY_UNAVAILABLE);
        }
        if (lookup == null || lookup.accountId() == null || lookup.accountId().isBlank()) {
            throw BusinessException.notFound(ErrorCode.ACCOUNT_NOT_FOUND, "Account", number);
        }
        return lookup;
    }

    /**
     * Mirrors a beneficiary lookup onto the ledger.
     * <p>
     * On first sight the receiver projection opens at zero: the lookup carries no
     * balance by design, and {@code account.opened} is the channel that announces
     * an account's opening balance. The balance is never overwritten on the update
     * path, so an existing projection keeps the money this service has moved.
     */
    private LedgerAccount projectFromLookup(AccountLookupResponse lookup, String requestedNumber) {
        LedgerAccount existing = repository.findByAccountId(lookup.accountId()).orElse(null);
        if (existing != null) {
            existing.setAccountNumber(resolveNumber(lookup, requestedNumber));
            existing.setUserId(lookup.userId());
            existing.setAccountType(lookup.accountType());
            existing.setCurrency(lookup.currency());
            existing.setStatus(lookup.status());
            return repository.save(existing);
        }
        LedgerAccount created = new LedgerAccount();
        created.setId(UUID.randomUUID().toString());
        created.setAccountId(lookup.accountId());
        created.setAccountNumber(resolveNumber(lookup, requestedNumber));
        created.setUserId(lookup.userId());
        created.setAccountType(lookup.accountType());
        created.setCurrency(lookup.currency());
        created.setBalance(Money.ZERO);
        created.setStatus(lookup.status());
        return repository.save(created);
    }

    private String resolveNumber(AccountLookupResponse lookup, String requestedNumber) {
        String returned = accountNumbers.normalise(lookup.accountNumber());
        return returned == null || returned.isEmpty() ? requestedNumber : returned;
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