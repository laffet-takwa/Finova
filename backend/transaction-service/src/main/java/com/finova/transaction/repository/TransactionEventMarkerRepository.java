package com.finova.transaction.repository;

import com.finova.transaction.domain.TransactionEventMarker;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TransactionEventMarkerRepository extends JpaRepository<TransactionEventMarker, String> {

    boolean existsByTopicAndEventId(String topic, String eventId);

    Optional<TransactionEventMarker> findByTopicAndEventId(String topic, String eventId);
}