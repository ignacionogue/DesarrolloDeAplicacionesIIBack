package com.example.demo.application.events;
import java.util.*;
public interface EventInbox {
    void receive(EventEnvelope event);
    String process(UUID id, EventHandlerRegistry registry);
    List<UUID> pending();
}
