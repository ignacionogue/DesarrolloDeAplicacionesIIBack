package com.example.demo.application.events;

import java.time.OffsetDateTime;
import java.util.*;
import tools.jackson.databind.JsonNode;

public interface ClosureIntegrationStore {
    record Closure(Long id, Long orderId, UUID requestId, UUID correlationId, UUID requestEventId,
            String state, JsonNode request, JsonNode authorization, JsonNode completion,
            OffsetDateTime authorizedStart, OffsetDateTime authorizedEnd, OffsetDateTime lastEventAt) {
        public String effectiveState(OffsetDateTime now) {
            if(!Set.of("APPROVED","ACTIVE","EXPIRED").contains(state)) return state;
            if(!now.isBefore(authorizedEnd)) return "EXPIRED";
            return now.isBefore(authorizedStart)?"APPROVED":"ACTIVE";
        }
    }
    void initialize(Long id, EventEnvelope request);
    Optional<Closure> lock(UUID requestId);
    List<Closure> forOrder(Long orderId);
    void apply(Closure existing, EventEnvelope response);
    void refreshWindows(OffsetDateTime now);
}
