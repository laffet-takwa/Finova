package com.finova.notification.repository;

import com.finova.common.domain.NotificationType;
import com.finova.notification.domain.Notification;
import com.finova.notification.domain.NotificationCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Inbox queries.
 * <p>
 * Every method that a customer can reach takes the caller id as an explicit
 * argument, and every mutation that has an ownership condition carries it in its
 * {@code WHERE} clause. The updates are therefore scoped by the database and not
 * by a preceding {@code SELECT}, which leaves no window between the check and the
 * write for a second request to slip through.
 */
@Repository
public interface NotificationRepository
        extends JpaRepository<Notification, String>, JpaSpecificationExecutor<Notification> {

    Optional<Notification> findByIdAndUserId(String id, String userId);

    Page<Notification> findByUserIdAndReadFalseOrderByCreatedAtDesc(String userId, Pageable pageable);

    boolean existsBySourceEventId(String sourceEventId);

    long countByUserId(String userId);

    long countByUserIdAndReadFalse(String userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification n
               set n.read = true, n.readAt = :readAt
             where n.id = :id and n.userId = :userId and n.read = false
            """)
    int markReadIfOwnedAndUnread(@Param("id") String id,
                                @Param("userId") String userId,
                                @Param("readAt") Instant readAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification n
               set n.read = true, n.readAt = :readAt
             where n.userId = :userId and n.read = false
            """)
    int markAllReadForUser(@Param("userId") String userId, @Param("readAt") Instant readAt);

    @Query("select n.category as category, count(n) as total from Notification n "
            + "where n.userId = :userId and n.read = false group by n.category")
    List<CategoryCount> countUnreadByCategory(@Param("userId") String userId);

    @Query("select n.type as type, count(n) as total from Notification n "
            + "where n.userId = :userId group by n.type")
    List<TypeCount> countAllByType(@Param("userId") String userId);

    @Query("select n.category as category, count(n) as total from Notification n "
            + "where n.userId = :userId group by n.category")
    List<CategoryCount> countAllByCategory(@Param("userId") String userId);

    @Query("select n.createdAt from Notification n where n.userId = :userId and n.createdAt >= :since")
    List<Instant> findCreatedSince(@Param("userId") String userId, @Param("since") Instant since);

    @Query("select n.createdAt from Notification n "
            + "where n.userId = :userId and n.createdAt >= :since and n.read = false")
    List<Instant> findUnreadCreatedSince(@Param("userId") String userId, @Param("since") Instant since);

    int deleteByReadTrueAndCreatedAtBefore(Instant cutoff);

    interface CategoryCount {
        NotificationCategory getCategory();

        long getTotal();
    }

    interface TypeCount {
        NotificationType getType();

        long getTotal();
    }
}