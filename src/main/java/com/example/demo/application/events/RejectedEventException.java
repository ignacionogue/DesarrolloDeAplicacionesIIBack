package com.example.demo.application.events;

/** Valid contract, but business transition cannot be applied. ACK after recording. */
public class RejectedEventException extends RuntimeException {
    public RejectedEventException() { super("BUSINESS_TRANSITION_REJECTED"); }
}
