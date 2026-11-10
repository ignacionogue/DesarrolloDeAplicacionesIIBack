package com.example.demo.application.events;

/** Port for validating M3-owned outgoing event contracts before they reach the Outbox. */
public interface OutgoingEventValidator {
    void validate(EventEnvelope event);
}
