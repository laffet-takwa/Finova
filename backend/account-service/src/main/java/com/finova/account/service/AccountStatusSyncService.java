package com.finova.account.service;

import com.finova.account.domain.Account;
import com.finova.account.repository.AccountRepository;
import com.finova.common.domain.AccountStatus;
import com.finova.common.event.AccountBlockedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Applies a block decided elsewhere (fraud-service) to the local account product.
 * <p>
 * The event may be one this service published itself, so a replay is a no-op
 * rather than an error. Nothing else about the account changes: the balance stays
 * a projection and no new event is produced.
 */
@Service
public class AccountStatusSyncService {

    private static final Logger log = LoggerFactory.getLogger(AccountStatusSyncService.class);

    private final AccountRepository accountRepository;
    private final SettlementAccountResolver settlementAccountResolver;

    public AccountStatusSyncService(AccountRepository accountRepository,
                                    SettlementAccountResolver settlementAccountResolver) {
        this.accountRepository = accountRepository;
        this.settlementAccountResolver = settlementAccountResolver;
    }

    /**
     * @return {@code true} when the local status changed
     */
    @Transactional
    public boolean blockLocally(AccountBlockedEvent event) {
        if (event == null) {
            return false;
        }
        Optional<Account> found = resolve(event);
        if (found.isEmpty()) {
            log.error("account.blocked references an unknown account accountId={} accountNumber={}",
                event.accountId(), event.accountNumber());
            return false;
        }
        Account account = found.get();
        if (account.getStatus() == AccountStatus.BLOCKED) {
            log.info("Account {} is already BLOCKED, nothing to sync", account.getId());
            return false;
        }
        if (account.getStatus() == AccountStatus.CLOSED) {
            log.warn("Ignoring account.blocked for closed account {}", account.getId());
            return false;
        }
        account.setStatus(AccountStatus.BLOCKED);
        accountRepository.save(account);
        log.info("Account {} blocked from account.blocked blockedBy={} reason={}",
            account.getId(), event.blockedBy(), event.reason());
        return true;
    }

    private Optional<Account> resolve(AccountBlockedEvent event) {
        if (event.accountId() != null && !event.accountId().isBlank()) {
            return accountRepository.findById(event.accountId());
        }
        return settlementAccountResolver.findByAccountNumber(event.accountNumber());
    }
}