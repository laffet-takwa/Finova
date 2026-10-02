package com.finova.common.error;

/**
 * Business exception carrying a canonical {@link ErrorCode}.
 * Services throw this from the service layer; {@code GlobalExceptionHandler} renders it.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient java.util.Map<String, String> details;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage(), null);
    }

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public BusinessException(ErrorCode errorCode, String message, java.util.Map<String, String> details) {
        super(message == null ? errorCode.defaultMessage() : message);
        this.errorCode = errorCode;
        this.details = details;
    }

    public static BusinessException notFound(ErrorCode code, String resource, Object id) {
        return new BusinessException(code, resource + " " + id + " was not found.");
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public java.util.Map<String, String> getDetails() {
        return details;
    }
}
