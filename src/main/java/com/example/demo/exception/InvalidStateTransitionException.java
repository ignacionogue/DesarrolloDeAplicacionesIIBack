package com.example.demo.exception;

/** Se lanza cuando se intenta una transicion de estado no permitida (ej. aprobar un proyecto que no esta pendiente). */
public class InvalidStateTransitionException extends ApiException {

    public InvalidStateTransitionException(String message) {
        super(message, ErrorCode.INVALID_STATE_TRANSITION);
    }
}
