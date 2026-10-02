package com.finova.notification.repository;

import com.finova.common.domain.NotificationType;
import com.finova.notification.domain.Notification;
import com.finova.notification.domain.NotificationCategory;
import com.finova.notification.dto.NotificationFilters;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns an inbox filter into a JPA {@link Specification}.
 * <p>
 * The owner is baked in by the factory method and is not overridable, so the
 * specification returned here is the complete authority on which rows a customer
 * can reach. The result is a value type: two calls with the same arguments produce
 * equal specifications, which is what makes the scoping assertable in a unit test.
 */
public final class NotificationSpecifications {

    private NotificationSpecifications() {
    }

    public static Specification<Notification> forUser(String userId, NotificationFilters filters) {
        return new UserInboxSpecification(userId, filters);
    }

    /**
     * Unscoped specification for the administrator feed. Only reachable from a
     * request that already cleared an ADMIN authorisation, which is why it is a
     * separate entry point rather than a flag on {@link #forUser}.
     */
    public static Specification<Notification> forAdmin(NotificationFilters filters) {
        return new AdminFeedSpecification(filters);
    }

    private record AdminFeedSpecification(NotificationFilters filters) implements Specification<Notification> {

        @Override
        public Predicate toPredicate(Root<Notification> root,
                                     CriteriaQuery<?> query,
                                     CriteriaBuilder builder) {
            return new UserInboxSpecification(null, filters).toPredicate(root, query, builder);
        }
    }

    private record UserInboxSpecification(String userId, NotificationFilters filters)
            implements Specification<Notification> {

        @Override
        public Predicate toPredicate(Root<Notification> root,
                                     CriteriaQuery<?> query,
                                     CriteriaBuilder builder) {
            List<Predicate> predicates = new ArrayList<>();
            if (userId != null) {
                predicates.add(builder.equal(root.get("userId"), userId));
            }

            if (filters != null) {
                NotificationType type = parseType(filters.type());
                if (type != null) {
                    predicates.add(builder.equal(root.get("type"), type));
                }
                NotificationCategory category = parseCategory(filters.category());
                if (category != null) {
                    predicates.add(builder.equal(root.get("category"), category));
                }
                if (filters.unreadOnly()) {
                    predicates.add(builder.isFalse(root.get("read")));
                }
                String search = filters.search();
                if (search != null && !search.isBlank()) {
                    String pattern = "%" + search.strip().toLowerCase() + "%";
                    predicates.add(builder.or(
                            builder.like(builder.lower(root.get("title")), pattern),
                            builder.like(builder.lower(root.get("message")), pattern)));
                }
                if (filters.from() != null) {
                    predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), filters.from()));
                }
                if (filters.to() != null) {
                    predicates.add(builder.lessThan(root.get("createdAt"), filters.to()));
                }
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        }
    }

    private static NotificationType parseType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (NotificationType type : NotificationType.values()) {
            if (type.name().equalsIgnoreCase(value.strip())) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown notification type: " + value);
    }

    private static NotificationCategory parseCategory(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return NotificationCategory.from(value);
    }
}