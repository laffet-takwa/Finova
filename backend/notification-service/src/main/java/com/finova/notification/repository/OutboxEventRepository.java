package com.finova.notification.repository;

import com.finova.notification.domain.OutboxEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

    Page<OutboxEvent> findByPublishedAtIsNullOrderByCreatedAtAsc(Pageable pageable);
}