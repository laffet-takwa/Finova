package com.finova.transaction.event;

import com.finova.common.event.AuditRecordEvent;
import com.finova.common.event.Topics;
import com.finova.common.event.TransactionEvent;
import com.finova.transaction.domain.Transaction;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the outbound {@code TransactionEvent} and {@code AuditRecordEvent}
 * payloads from a transaction, so every topic carries exactly the same shape.
 */
@Component
public class TransactionEventFactory {

    public TransactionEvent from(Transaction transaction) {
        return new TransactionEvent(
                transaction.getId(),
                transaction.getReference(),
                transaction.getSenderAccountId(),
                transaction.getReceiverAccountId(),
                transaction.getSenderAccountNumber(),
                transaction.getReceiverAccountNumber(),
                transaction.getSenderUserId(),
                transaction.getReceiverUserId(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getDescription(),
                transaction.getType(),
                transaction.getStatus(),
                transaction.getRiskScore(),
                transaction.getRiskLevel(),
                transaction.getRiskReasons(),
                transaction.getFailureReason(),
                transaction.getRequestedByUserId(),
                transaction.getIpAddress(),
                transaction.getCompletedAt());
    }

    public AuditRecordEvent audit(Transaction transaction, String action, String result, String userId,
                                  String correlationId) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("reference", transaction.getReference());
        metadata.put("amount", String.valueOf(transaction.getAmount()));
        metadata.put("currency", transaction.getCurrency());
        return new AuditRecordEvent(
                action,
                userId,
                "transaction",
                transaction.getId(),
                transaction.getIpAddress(),
                correlationId,
                result,
                "transaction-service",
                action + " for reference " + transaction.getReference(),
                metadata,
                transaction.getCompletedAt() == null ? transaction.getCreatedAt() : transaction.getCompletedAt());
    }

    public String auditTopic() {
        return Topics.AUDIT_RECORDED;
    }
}