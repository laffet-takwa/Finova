package com.finova.transaction.exception;

import com.finova.common.error.ApiError;
import com.finova.common.error.ErrorCode;
import com.finova.common.web.CorrelationIdFilter;
import com.finova.common.web.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.ServletRequestBindingException;

/**
 * Adds the lock-contention mappings this service needs on top of the platform
 * handler.
 * <p>
 * Under concurrent opposite-direction transfers the second settlement blocks on
 * the first ledger row rather than deadlocking, and a lock timeout is a retryable
 * condition - never a lost transfer and never a 500.
 */
@RestControllerAdvice
public class TransactionExceptionHandler extends GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(TransactionExceptionHandler.class);

    @ExceptionHandler({PessimisticLockingFailureException.class, CannotAcquireLockException.class})
    public ResponseEntity<ApiError> handleLockContention(Exception ex, HttpServletRequest request) {
        log.warn("Ledger lock contention on {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorCode code = ErrorCode.SERVICE_UNAVAILABLE;
        ApiError body = ApiError.of(code.httpStatus(), code,
                "The ledger is busy with a concurrent operation. Please retry.", request.getRequestURI(),
                CorrelationIdFilter.current(request), null);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    /**
     * A malformed request the platform handler does not cover - a missing query
     * parameter, an unparseable enum or number, an unparseable instant. These are
     * client mistakes, so they are a 400 and never a 500.
     */
    @ExceptionHandler({ServletRequestBindingException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ApiError> handleBinding(Exception ex, HttpServletRequest request) {
        log.warn("Request binding rejected {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorCode code = ErrorCode.VALIDATION_ERROR;
        ApiError body = ApiError.of(code.httpStatus(), code, code.defaultMessage(), request.getRequestURI(),
                CorrelationIdFilter.current(request), null);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}