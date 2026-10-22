package com.example.demo.application.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Transport-neutral contract. Never log the data field. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventEnvelope(UUID eventId, String eventType, String eventVersion,
        OffsetDateTime occurredAt, String sourceModule, UUID correlationId,
        UUID causationId, JsonNode data) {
    public EventEnvelope {
        if (eventId == null || eventId.version() != 4 || eventId.variant() != 2 || occurredAt == null
                || eventType == null || !eventType.matches("[a-z][a-zA-Z0-9]{0,119}")
                || !"1.0".equals(eventVersion) || sourceModule == null
                || !sourceModule.matches("[a-z][a-z0-9-]{0,59}") || data == null || !data.isObject()
                || eventId.equals(causationId)) throw new InvalidEventException();
        data = data.deepCopy();
    }
    public static EventEnvelope outgoing(String type, JsonNode data, UUID correlation, UUID cause) {
        return new EventEnvelope(UUID.randomUUID(), type, "1.0", OffsetDateTime.now(java.time.ZoneOffset.UTC),
                "obras", correlation == null ? UUID.randomUUID() : correlation, cause, data);
    }
    @Override public JsonNode data() { return data.deepCopy(); }
    @Override public String toString() {
        return "EventEnvelope[eventId=" + eventId + ", eventType=" + eventType + ", data=redacted]";
    }
}
