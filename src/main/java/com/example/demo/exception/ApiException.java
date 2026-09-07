package com.example.demo.exception;

/**
 * Excepcion base para errores de negocio conocidos. Cada subclase representa
 * un ErrorCode especifico, y el GlobalExceptionHandler las traduce al formato
 * de respuesta estandar sin exponer stacktraces.
 */
public abstract class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    protected ApiException(String message, ErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
