package com.finova.fraud.repository;

import com.finova.fraud.domain.RecipientHistory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecipientHistoryRepository extends MongoRepository<RecipientHistory, String> {
}
