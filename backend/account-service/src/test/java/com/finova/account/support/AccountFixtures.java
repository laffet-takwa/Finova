package com.finova.account.support;

import com.finova.account.domain.Account;
import com.finova.common.domain.AccountStatus;
import com.finova.common.domain.AccountType;
import com.finova.common.domain.Currency;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class AccountFixtures {

    public static final String ACCOUNT_NUMBER = "TN58100001234567890123";
    public static final String IBAN = "TN565900058100001234567890123";

    private AccountFixtures() {
    }

    public static Account account(String id, String userId, AccountStatus status, BigDecimal balance) {
        return account(id, userId, ACCOUNT_NUMBER, AccountType.CHECKING, Currency.TND, balance, status);
    }

    public static Account account(String id, String userId, String accountNumber, AccountType accountType,
                                  Currency currency, BigDecimal balance, AccountStatus status) {
        Account account = new Account();
        account.setId(id);
        account.setUserId(userId);
        account.setAccountNumber(accountNumber);
        account.setAccountType(accountType);
        account.setCurrency(currency);
        account.setBalance(balance);
        account.setStatus(status);
        account.setNickname("Everyday Account");
        account.setIban(IBAN);
        account.setCreatedAt(Instant.parse("2026-01-15T10:00:00Z"));
        account.setUpdatedAt(Instant.parse("2026-01-15T10:00:00Z"));
        return account;
    }

    public static String randomId() {
        return UUID.randomUUID().toString();
    }
}
