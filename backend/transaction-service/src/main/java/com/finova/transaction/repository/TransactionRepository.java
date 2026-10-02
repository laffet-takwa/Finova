package com.finova.transaction.repository;

import com.finova.common.domain.TransactionStatus;
import com.finova.common.domain.TransactionType;
import com.finova.transaction.domain.Transaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, String>,
        JpaSpecificationExecutor<Transaction> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    Optional<Transaction> findByReference(String reference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Transaction t where t.id = :id")
    Optional<Transaction> findByIdForUpdate(@Param("id") String id);

    Page<Transaction> findByStatus(TransactionStatus status, Pageable pageable);

    long countByStatus(TransactionStatus status);

    long countByStatusAndCreatedAtAfter(TransactionStatus status, Instant after);

    long countByStatusAndCompletedAtAfter(TransactionStatus status, Instant after);

    @Query("select t.currency, sum(t.amount) from Transaction t "
            + "where t.status = 'COMPLETED' and t.completedAt >= :from group by t.currency")
    List<Object[]> volumeByCurrencySince(@Param("from") Instant from);

    @Query("select avg(t.amount), max(t.amount) from Transaction t where t.status = 'COMPLETED'")
    Object[] averageAndLargestCompletedAmount();

    @Query("select t.status, count(t) from Transaction t group by t.status")
    List<Object[]> countGroupByStatus();

    @Query("select t.type, count(t) from Transaction t group by t.type")
    List<Object[]> countGroupByType();

    /**
     * Completed legs visible to one holder, used for the dashboard series. Only
     * the three columns needed for bucketing are selected.
     */
    @Query("select t.completedAt, t.amount, t.senderUserId, t.receiverUserId from Transaction t "
            + "where t.status = 'COMPLETED' and t.completedAt >= :from and t.completedAt < :to "
            + "and (t.senderUserId = :userId or t.receiverUserId = :userId) order by t.completedAt asc")
    List<Object[]> findCompletedLegsForHolder(@Param("userId") String userId,
                                             @Param("from") Instant from,
                                             @Param("to") Instant to);

    @Query("select sum(t.amount) from Transaction t "
            + "where t.status = 'COMPLETED' and t.senderUserId = :userId "
            + "and t.completedAt >= :from and t.completedAt < :to")
    BigDecimal sumExpensesForHolder(@Param("userId") String userId,
                                    @Param("from") Instant from,
                                    @Param("to") Instant to);

    @Query("select sum(t.amount) from Transaction t "
            + "where t.status = 'COMPLETED' and t.receiverUserId = :userId "
            + "and t.completedAt >= :from and t.completedAt < :to")
    BigDecimal sumIncomeForHolder(@Param("userId") String userId,
                                  @Param("from") Instant from,
                                  @Param("to") Instant to);

    @Query("select t from Transaction t where t.status = 'COMPLETED' and t.completedAt >= :from "
            + "and t.completedAt < :to order by t.completedAt asc")
    List<Transaction> findCompletedBetween(@Param("from") Instant from, @Param("to") Instant to);

    List<Transaction> findBySenderUserIdOrReceiverUserIdOrderByCreatedAtDesc(String senderUserId,
                                                                             String receiverUserId,
                                                                             Pageable pageable);

    Page<Transaction> findByType(TransactionType type, Pageable pageable);
}