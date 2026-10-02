package com.finova.account.repository;

import com.finova.account.domain.Account;
import com.finova.common.domain.AccountStatus;
import com.finova.common.domain.AccountType;
import com.finova.common.domain.Currency;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, String>, JpaSpecificationExecutor<Account> {

    List<Account> findByUserIdOrderByCreatedAtDesc(String userId);

    Page<Account> findByUserId(String userId, Pageable pageable);

    List<Account> findByUserId(String userId);

    Optional<Account> findByAccountNumber(String normalisedAccountNumber);

    boolean existsByAccountNumber(String normalisedAccountNumber);

    boolean existsByUserIdAndAccountTypeAndCurrency(String userId, AccountType accountType, Currency currency);

    long countByUserId(String userId);

    long countByStatus(AccountStatus status);

    long countByCreatedAtAfter(Instant instant);

    @Query("select coalesce(sum(a.balance), 0) from Account a where a.currency = :currency")
    BigDecimal totalBalanceByCurrency(@Param("currency") Currency currency);

    @Query("select a.currency as currency, coalesce(sum(a.balance), 0) as total "
        + "from Account a group by a.currency order by a.currency")
    List<CurrencyBalance> totalBalanceGroupedByCurrency();

    @Query("select a.accountType as accountType, count(a) as total from Account a "
        + "group by a.accountType order by a.accountType")
    List<AccountTypeCount> countGroupedByType();

    @Query("select a.createdAt as createdAt, a.balance as balance from Account a where a.createdAt >= :from")
    List<AccountCreatedPoint> accountsCreatedSince(@Param("from") Instant from);

    interface CurrencyBalance {
        Currency getCurrency();

        BigDecimal getTotal();
    }

    interface AccountTypeCount {
        AccountType getAccountType();

        long getTotal();
    }

    interface AccountCreatedPoint {
        Instant getCreatedAt();

        BigDecimal getBalance();
    }
}
