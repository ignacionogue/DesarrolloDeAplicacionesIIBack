package com.example.demo.application.events;

import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class EventHandlerRegistry {
    private final Map<String, EventHandler> handlers;
    public EventHandlerRegistry(List<EventHandler> handlers) {
        var index = new HashMap<String, EventHandler>();
        for (var handler : handlers) {
            if (index.putIfAbsent(handler.eventType(), handler) != null)
                throw new IllegalStateException("Duplicate event handler");
        }
        this.handlers = Map.copyOf(index);
    }
    public Optional<EventHandler> find(EventEnvelope event) {
        var handler = handlers.get(event.eventType());
        if (handler != null && !handler.sourceModule().equals(event.sourceModule())) throw new InvalidEventException();
        return Optional.ofNullable(handler);
    }
}
