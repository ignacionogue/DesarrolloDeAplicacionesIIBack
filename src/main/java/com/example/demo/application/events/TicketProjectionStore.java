package com.example.demo.application.events;

import tools.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.UUID;

public interface TicketProjectionStore {
    record Projection(UUID ticketId,JsonNode snapshot,OffsetDateTime updatedAt,JsonNode routing,
            OffsetDateTime routingAt,JsonNode information,OffsetDateTime informationAt,JsonNode escalation,
            OffsetDateTime escalationAt,UUID correlationId,Long orderId,boolean started,
            boolean planningReviewRequired,boolean evaluationRequired) { }
    Projection lockOrCreate(EventEnvelope event);
    java.util.Optional<Projection> lock(java.util.UUID ticketId);
    void save(Projection projection);
}
