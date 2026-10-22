package com.example.demo.application.events;

public interface EventTransport {
    /** Returns only after broker confirmation, throws on timeout, return or nack. */
    void publish(EventEnvelope event);
}
