package com.example.demo.application.events;

/** Intentionally excludes payload, parser messages and validation values. */
public class InvalidEventException extends RuntimeException {
    public InvalidEventException() { super("INVALID_EVENT"); }
}
