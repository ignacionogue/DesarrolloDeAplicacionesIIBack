package com.example.demo.application.events;

public interface EventOutbox {
    /** Must join the business transaction. Ordering key serializes facts for one case. */
    void append(EventEnvelope event, String orderingKey);
}
