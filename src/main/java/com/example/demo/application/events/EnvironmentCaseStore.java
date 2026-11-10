package com.example.demo.application.events;

import java.util.UUID;
import java.util.Optional;

public interface EnvironmentCaseStore {
    record Case(UUID sourceId, String eventType, UUID correlationId, Long orderId, boolean actionable) { }
    void receive(EventEnvelope event, String idField, String timestampField, boolean actionable);
    Optional<Case> lock(UUID sourceId);
    void link(UUID sourceId, Long orderId);
}
