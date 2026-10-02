package com.finova.transaction.repository;

import com.finova.transaction.domain.LedgerAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, String> {

    Optional<LedgerAccount> findByAccountId(String accountId);

    Optional<LedgerAccount> findByAccountNumber(String accountNumber);

    List<LedgerAccount> findByUserId(String userId);

    /** Plain read of the two primary keys, used to establish a global lock order. */
    @Query("select a.id from LedgerAccount a where a.accountId in :accountIds")
    List<String> findIdsByAccountIdIn(@Param("accountIds") List<String> accountIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from LedgerAccount a where a.id = :id")
    Optional<LedgerAccount> findByIdForUpdate(@Param("id") String id);
}