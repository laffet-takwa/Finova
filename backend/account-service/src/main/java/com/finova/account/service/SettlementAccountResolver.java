package com.finova.account.service;

import com.finova.account.domain.Account;
import com.finova.account.repository.AccountRepository;
import com.finova.account.support.AccountNumbers;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Ownership-free account resolution for machine callers (Kafka settlement and
 * the transaction-service).
 * <p>
 * This is deliberately a separate component from {@link AccountService}: the
 * user facing methods there always run an ownership check, while a settlement
 * leg legitimately has to resolve the counterparty account by number. Nothing
 * that renders a customer facing payload may call this class directly; it must
 * go through {@code AccountService} so the access rules stay in one place.
 */
@Component
public class SettlementAccountResolver {

    private final AccountRepository accountRepository;

    public SettlementAccountResolver(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public Optional<Account> findByAccountNumber(String accountNumber) {
        String normalised = AccountNumbers.normalise(accountNumber);
        if (normalised == null || normalised.isBlank()) {
            return Optional.empty();
        }
        return accountRepository.findByAccountNumber(normalised);
    }
}
