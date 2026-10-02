package com.finova.transaction.repository;

import com.finova.transaction.domain.LedgerEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LedgerEventRepository extends JpaRepository<LedgerEvent, String> {

    List<LedgerEvent> findByTransactionIdOrderByCreatedAtAsc(String transactionId);

    long countByTransactionId(String transactionId);
}