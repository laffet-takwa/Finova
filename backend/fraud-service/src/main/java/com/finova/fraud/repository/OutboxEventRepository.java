package com.finova.fraud.repository;

import com.finova.fraud.domain.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface OutboxEventRepository extends MongoRepository<OutboxEvent, String> {

    List<OutboxEvent> findByPublishedAtIsNullOrderByCreatedAtAsc(Pageable pageable);

    boolean existsByDedupeKey(String dedupeKey);

    long countByPublishedAtIsNull();

    long deleteByPublishedAtBefore(Instant cutoff);
}
