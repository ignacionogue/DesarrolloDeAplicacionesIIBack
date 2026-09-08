package com.example.demo.exception;

/** Se lanza cuando un valor del request (ej. un enum en texto) no es valido. */
public class InvalidRequestException extends ApiException {

    public InvalidRequestException(String message) {
        super(message, ErrorCode.VALIDATION_ERROR);
    }
}
