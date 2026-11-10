package com.example.demo.exception;

public class PreconditionFailedException extends ApiException {
    public PreconditionFailedException(String message) {
        super(message, ErrorCode.PRECONDITION_FAILED);
    }
}
