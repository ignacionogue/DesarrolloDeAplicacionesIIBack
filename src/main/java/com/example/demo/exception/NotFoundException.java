package com.example.demo.exception;

/** Se lanza cuando se busca una entidad por id y no existe. */
public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(message, ErrorCode.NOT_FOUND);
    }
}
