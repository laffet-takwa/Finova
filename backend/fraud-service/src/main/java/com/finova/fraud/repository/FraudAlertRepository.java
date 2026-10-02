package com.finova.fraud.repository;

import com.finova.fraud.domain.FraudAlert;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface FraudAlertRepository extends MongoRepository<FraudAlert, String> {

    Optional<FraudAlert> findByTransactionId(String transactionId);

    boolean existsByTransactionId(String transactionId);

    List<FraudAlert> findByStatusInOrderByCreatedAtDesc(List<String> statuses);

    List<FraudAlert> findBySenderAccountIdOrderByCreatedAtDesc(String senderAccountId);

    List<FraudAlert> findByRiskLevelAndStatusInOrderByCreatedAtDesc(String riskLevel,
                                                                  List<String> statuses);

    long countByStatus(String status);

    long countByStatusIn(List<String> statuses);

    long countByRiskLevelAndStatusIn(String riskLevel, List<String> statuses);

    long countByCreatedAtGreaterThanEqual(Instant from);

    long countByUpdatedAtGreaterThanEqualAndStatusIn(Instant from, List<String> statuses);

    long countByUpdatedAtGreaterThanEqualAndStatus(Instant from, String status);
}
