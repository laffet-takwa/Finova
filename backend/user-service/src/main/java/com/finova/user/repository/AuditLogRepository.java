package com.finova.user.repository;

import com.finova.user.domain.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * The append-only audit trail.
 * <p>
 * {@code existsByEventId} is the consumer's idempotency check: it runs before every
 * insert, and the partial unique index on {@code event_id} is what makes the check
 * safe when two consumer threads race each other on the same event.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, String>,
        JpaSpecificationExecutor<AuditLog> {

    boolean existsByEventId(String eventId);

    long countByAction(String action);

    long countByResult(String result);

    long countByUserIdAndAction(String userId, String action);

    @Query("select a.action as action, count(a) as total from AuditLog a group by a.action")
    List<ActionCount> countGroupedByAction();

    /** Newest-first security history for the signed-in customer's security panel. */
    List<AuditLog> findByUserIdAndActionInOrderByCreatedAtDesc(String userId, Collection<String> actions,
                                                              Pageable pageable);

    @Modifying
    @Query("delete from AuditLog a where a.createdAt < :cutoff")
    int deleteCreatedBefore(@Param("cutoff") Instant cutoff);

    interface ActionCount {
        String getAction();

        long getTotal();
    }
}
