package com.example.demo.infrastructure.integration;

import com.example.demo.application.events.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.*;

@Component
public class EventCodec implements EventSerialization {
    private static final Set<String> FIELDS = Set.of("eventId", "eventType", "eventVersion", "occurredAt",
            "sourceModule", "correlationId", "causationId", "data");
    private final ObjectMapper json;
    public EventCodec(ObjectMapper json) { this.json = json; }
    public EventEnvelope decode(byte[] bytes) {
        if (bytes.length > 1024 * 1024) throw new InvalidEventException();
        try {
            var tree = json.readTree(bytes);
            if (!tree.isObject()) throw new InvalidEventException();
            for (String name : tree.propertyNames()) if (!FIELDS.contains(name)) throw new InvalidEventException();
            if (!tree.path("occurredAt").asString("").matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:\\d{2})"))
                throw new InvalidEventException();
            return new EventEnvelope(uuid(tree.path("eventId").asString()), tree.path("eventType").asString(),
                    tree.has("eventVersion") ? tree.path("eventVersion").asString() : "1.0",
                    OffsetDateTime.parse(tree.path("occurredAt").asString()), tree.path("sourceModule").asString(),
                    optionalUuid(tree.get("correlationId")), optionalUuid(tree.get("causationId")), tree.get("data"));
        } catch (RuntimeException ex) { throw new InvalidEventException(); }
    }
    public String encode(EventEnvelope event) { return json.writeValueAsString(event); }
    private UUID optionalUuid(tools.jackson.databind.JsonNode node) {
        return node == null || node.isNull() ? null : uuid(node.asString());
    }
    public static UUID uuid(String value) {
        if (value == null || !value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw new InvalidEventException();
        return UUID.fromString(value);
    }
}
