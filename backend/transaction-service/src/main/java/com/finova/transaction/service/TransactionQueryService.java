package com.finova.transaction.service;

import com.finova.common.error.BusinessException;
import com.finova.common.error.ErrorCode;
import com.finova.common.domain.TransactionStatus;
import com.finova.common.domain.TransactionType;
import com.finova.common.support.Money;
import com.finova.transaction.config.TransactionProperties;
import com.finova.transaction.domain.Transaction;
import com.finova.transaction.dto.TransactionQuery;
import com.finova.transaction.repository.TransactionRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reads the transfer history.
 * <p>
 * Customer queries are always narrowed to the caller's own legs unless an
 * administrator asks for someone else explicitly, so a query parameter can
 * never widen a customer's visibility.
 */
@Service
@Transactional(readOnly = true)
public class TransactionQueryService {

    private static final Set<String> SORTABLE = Set.of("createdAt", "completedAt", "amount", "status", "type",
            "reference");

    private final TransactionRepository repository;
    private final TransactionProperties properties;

    public TransactionQueryService(TransactionRepository repository, TransactionProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public Page<Transaction> search(TransactionQuery query, String userId, String scopeUserId) {
        return repository.findAll(specification(query, userId, scopeUserId), pageable(query));
    }

    public Transaction requireVisible(String transactionId, String userId, boolean admin) {
        Transaction transaction = repository.findById(transactionId)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.TRANSACTION_NOT_FOUND,
                        "Transaction", transactionId));
        if (!admin && !isHolder(transaction, userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "You are not allowed to access this resource.");
        }
        return transaction;
    }

    boolean isHolder(Transaction transaction, String userId) {
        return userId != null && (userId.equals(transaction.getSenderUserId())
                || userId.equals(transaction.getReceiverUserId()));
    }

    private Specification<Transaction> specification(TransactionQuery query, String userId, String scopeUserId) {
        String effectiveScope = scopeUserId == null ? userId : scopeUserId;
        return (root, criteriaQuery, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (effectiveScope != null && !effectiveScope.isBlank()) {
                predicates.add(builder.or(
                        builder.equal(root.get("senderUserId"), effectiveScope),
                        builder.equal(root.get("receiverUserId"), effectiveScope)));
            }
            if (query.search() != null && !query.search().isBlank()) {
                String pattern = "%" + query.search().trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("description")), pattern),
                        builder.like(builder.lower(root.get("reference")), pattern)));
            }
            if (query.type() != null) {
                predicates.add(builder.equal(root.get("type"), query.type().name()));
            }
            if (query.status() != null) {
                predicates.add(builder.equal(root.get("status"), query.status().name()));
            }
            if (query.accountId() != null && !query.accountId().isBlank()) {
                String accountId = query.accountId().trim();
                predicates.add(builder.or(
                        builder.equal(root.get("senderAccountId"), accountId),
                        builder.equal(root.get("receiverAccountId"), accountId)));
            }
            if (query.currency() != null) {
                predicates.add(builder.equal(root.get("currency"), query.currency().name()));
            }
            if (query.minAmount() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("amount"), Money.scale(query.minAmount())));
            }
            if (query.maxAmount() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("amount"), Money.scale(query.maxAmount())));
            }
            if (query.from() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), query.from()));
            }
            if (query.to() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("createdAt"), query.to()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    /**
     * Pagination and sort are clamped here so a hostile {@code size} or
     * {@code sort} cannot reach the database.
     */
    Pageable pageable(TransactionQuery query) {
        int size = Math.min(Math.max(query.size() <= 0 ? properties.getDefaultPageSize() : query.size(), 1),
                properties.getMaxPageSize());
        int page = Math.max(query.page(), 0);
        return PageRequest.of(page, size, sort(query.sort()));
    }

    Sort sort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Order.desc("createdAt"));
        }
        String[] parts = sort.split(",", 2);
        String field = parts[0].trim();
        if (!SORTABLE.contains(field)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Unsupported sort field '" + field + "'. Allowed: " + String.join(", ", SORTABLE) + ".");
        }
        Sort.Direction direction = parts.length < 2 || !"asc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(new Sort.Order(direction, field));
    }

    public Transaction requireForUpdate(String transactionId) {
        return repository.findById(transactionId)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.TRANSACTION_NOT_FOUND,
                        "Transaction", transactionId));
    }

    public Page<Transaction> byStatus(TransactionStatus status, Pageable pageable) {
        return repository.findByStatus(status, pageable);
    }

    public Page<Transaction> byType(TransactionType type, Pageable pageable) {
        return repository.findByType(type, pageable);
    }
}