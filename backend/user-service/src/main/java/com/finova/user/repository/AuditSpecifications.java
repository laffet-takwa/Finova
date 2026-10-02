package com.finova.user.repository;

import com.finova.common.domain.Role;
import com.finova.common.domain.UserStatus;
import com.finova.user.domain.AuditLog;
import com.finova.user.domain.User;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Filter predicates for the two admin screens.
 * <p>
 * Each predicate is optional and additive, so an unfiltered admin request degrades to
 * "everything, newest first" rather than to an error. The sort is applied by the
 * service through {@code Pageable}, never by a caller-supplied sort expression, which
 * keeps a user input string out of the {@code ORDER BY} clause.
 */
public final class AuditSpecifications {

    private AuditSpecifications() {
    }

    public static Specification<User> search(String search, Role role, UserStatus status) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("email")), like),
                        builder.like(builder.lower(root.get("firstName")), like),
                        builder.like(builder.lower(root.get("lastName")), like)));
            }
            if (role != null) {
                predicates.add(builder.equal(root.get("role"), role));
            }
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            return predicates.isEmpty() ? builder.conjunction()
                    : builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<AuditLog> filter(String userId, String action, String result,
                                                  String search, Instant from, Instant to) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (userId != null && !userId.isBlank()) {
                predicates.add(builder.equal(root.get("userId"), userId.trim()));
            }
            if (action != null && !action.isBlank()) {
                predicates.add(builder.equal(root.get("action"), action.trim().toUpperCase(Locale.ROOT)));
            }
            if (result != null && !result.isBlank()) {
                predicates.add(builder.equal(root.get("result"), result.trim().toUpperCase(Locale.ROOT)));
            }
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("message")), like),
                        builder.like(builder.lower(root.get("resource")), like),
                        builder.like(builder.lower(root.get("service")), like)));
            }
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return predicates.isEmpty() ? builder.conjunction()
                    : builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** Actions that describe a sign-in attempt, used by the customer's security panel. */
    public static final Collection<String> LOGIN_ACTIONS = List.of("LOGIN_SUCCESS", "LOGIN_FAILED");
}
