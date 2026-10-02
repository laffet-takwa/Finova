package com.finova.transaction.service;

import com.finova.common.error.BusinessException;
import com.finova.common.error.ErrorCode;
import com.finova.transaction.domain.LedgerAccount;
import com.finova.transaction.domain.Transaction;
import com.finova.transaction.dto.TransferOutcome;
import com.finova.transaction.dto.TransferRequest;
import com.finova.transaction.mapper.TransactionMapper;
import com.finova.transaction.repository.TransactionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Accepts a transfer, in the order the client specification mandates.
 * <p>
 * The method is deliberately not transactional: it reads, validates and then
 * hands the single insert to {@link TransferPersistence}. That split keeps the
 * idempotent replay path usable after a unique-violation rollback, because the
 * replay lookup then happens in a fresh transaction.
 */
@Service
public class TransferService {

    public static final int MIN_IDEMPOTENCY_KEY_LENGTH = 8;
    public static final int MAX_IDEMPOTENCY_KEY_LENGTH = 80;

    private final TransactionRepository transactionRepository;
    private final TransferRequestValidator validator;
    private final LedgerProjectionService projections;
    private final RequestFingerprint fingerprint;
    private final TransferPersistence persistence;
    private final TransactionMapper mapper;

    public TransferService(TransactionRepository transactionRepository,
                           TransferRequestValidator validator,
                           LedgerProjectionService projections,
                           RequestFingerprint fingerprint,
                           TransferPersistence persistence,
                           TransactionMapper mapper) {
        this.transactionRepository = transactionRepository;
        this.validator = validator;
        this.projections = projections;
        this.fingerprint = fingerprint;
        this.persistence = persistence;
        this.mapper = mapper;
    }

    /**
     * @param idempotencyKey   mandatory {@code Idempotency-Key} header
     * @param requestedByUserId authenticated caller
     * @param admin            {@code true} when the caller holds the ADMIN role
     * @param ipAddress        caller's address, recorded for the audit trail
     */
    public TransferOutcome create(TransferRequest request, String idempotencyKey, String requestedByUserId,
                                  boolean admin, String ipAddress) {
        String key = requireIdempotencyKey(idempotencyKey);
        String requestFingerprint = fingerprint.of(request);

        Optional<Transaction> replay = transactionRepository.findByIdempotencyKey(key);
        if (replay.isPresent()) {
            return new TransferOutcome(resolveReplay(replay.get(), requestFingerprint), true);
        }

        LedgerAccount sender = projections.resolveByAccountId(request.senderAccountId());
        LedgerAccount receiver = projections.resolveByAccountNumber(request.receiverAccountNumber());
        TransferValidation validation = validator.validate(request, sender, receiver, requestedByUserId, admin);

        try {
            Transaction created = persistence.insert(request, key, requestFingerprint, sender, receiver,
                    validation, requestedByUserId, ipAddress);
            return new TransferOutcome(mapper.toResponse(created), false);
        } catch (DataIntegrityViolationException ex) {
            Optional<Transaction> concurrent = transactionRepository.findByIdempotencyKey(key);
            if (concurrent.isEmpty()) {
                throw ex;
            }
            return new TransferOutcome(resolveReplay(concurrent.get(), requestFingerprint), true);
        }
    }

    private com.finova.transaction.dto.TransactionResponse resolveReplay(Transaction existing,
                                                                          String requestFingerprint) {
        if (!existing.getRequestFingerprint().equals(requestFingerprint)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    ErrorCode.IDEMPOTENCY_KEY_REUSED.defaultMessage());
        }
        return mapper.toResponse(existing);
    }

    /** Rule 10: the header is mandatory and long enough to be a real client key. */
    private String requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "The Idempotency-Key header is required for transfer creation.");
        }
        String key = idempotencyKey.trim();
        if (key.length() < MIN_IDEMPOTENCY_KEY_LENGTH || key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "The Idempotency-Key header must be between " + MIN_IDEMPOTENCY_KEY_LENGTH + " and "
                            + MAX_IDEMPOTENCY_KEY_LENGTH + " characters.");
        }
        return key;
    }
}