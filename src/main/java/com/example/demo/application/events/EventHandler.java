package com.example.demo.application.events;

public interface EventHandler {
    String eventType();
    String sourceModule();
    /** Executed before Inbox persistence, to avoid storing unrelated business data. */
    boolean relevant(EventEnvelope event);
    void validate(EventEnvelope event);
    /** Remove fields not required for M3, including third-party personal identifiers. */
    default EventEnvelope minimize(EventEnvelope event) { return event; }
    /** Called with the Inbox row locked, inside the same transaction as all effects. */
    void handle(EventEnvelope event);
}
