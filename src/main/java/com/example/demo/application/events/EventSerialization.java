package com.example.demo.application.events;
public interface EventSerialization {
    EventEnvelope decode(byte[] bytes);
    String encode(EventEnvelope event);
}
