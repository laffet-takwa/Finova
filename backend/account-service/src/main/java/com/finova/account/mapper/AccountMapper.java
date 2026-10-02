package com.finova.account.mapper;

import com.finova.account.domain.Account;
import com.finova.account.dto.AccountLookupResponse;
import com.finova.account.dto.AccountResponse;
import com.finova.account.dto.BalanceResponse;
import com.finova.account.support.AccountNumbers;
import com.finova.common.support.Money;
import org.mapstruct.Mapper;

import java.math.BigDecimal;
import java.time.Instant;

@Mapper(componentModel = "spring")
public interface AccountMapper {

    default AccountResponse toResponse(Account account) {
        String accountNumber = account.getAccountNumber();
        BigDecimal balance = Money.scale(account.getBalance());
        return new AccountResponse(
            account.getId(),
            AccountNumbers.format(accountNumber),
            AccountNumbers.mask(accountNumber),
            account.getUserId(),
            account.getAccountType(),
            account.getCurrency(),
            balance,
            balance,
            account.getStatus(),
            account.getNickname(),
            account.getIban(),
            account.getCreatedAt(),
            account.getUpdatedAt(),
            AccountLookupResponse.BANK_NAME);
    }

    default BalanceResponse toBalance(Account account) {
        BigDecimal balance = Money.scale(account.getBalance());
        return new BalanceResponse(
            account.getId(),
            AccountNumbers.format(account.getAccountNumber()),
            account.getCurrency(),
            balance,
            balance,
            account.getStatus(),
            Instant.now());
    }

    default AccountLookupResponse toLookup(Account account) {
        String accountNumber = account.getAccountNumber();
        String masked = AccountNumbers.mask(accountNumber);
        return new AccountLookupResponse(
            AccountNumbers.format(accountNumber),
            masked,
            account.getAccountType(),
            account.getCurrency(),
            masked,
            account.getStatus(),
            AccountLookupResponse.BANK_NAME);
    }
}
