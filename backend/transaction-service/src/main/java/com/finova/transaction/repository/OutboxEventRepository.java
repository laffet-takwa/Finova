package com.finova.transaction.repository;

import com.finova.transaction.domain.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

    @Query("select e from OutboxEvent e where e.publishedAt is null order by e.createdAt asc")
    List<OutboxEvent> findUnpublished(Pageable pageable);

    List<OutboxEvent> findByTopicAndEventKey(String topic, String eventKey);
}