package com.example.demo.application.events;

import tools.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.UUID;

final class EnvironmentEventFields {
    private EnvironmentEventFields() { }
    static void text(JsonNode data, String field) {
        if(!data.path(field).isString() || data.path(field).asString().isBlank()) throw new InvalidEventException();
    }
    static void uuid(JsonNode data, String field) {
        text(data,field);
        try { if(!UUID.fromString(data.path(field).asString()).toString().equalsIgnoreCase(data.path(field).asString())) throw new InvalidEventException(); }
        catch(IllegalArgumentException ex) { throw new InvalidEventException(); }
    }
    static void time(JsonNode data, String field) {
        text(data,field);
        try { OffsetDateTime.parse(data.path(field).asString()); }
        catch(java.time.DateTimeException ex) { throw new InvalidEventException(); }
    }
    static void bool(JsonNode data,String field) { if(!data.path(field).isBoolean()) throw new InvalidEventException(); }
    static void location(JsonNode data) { if(!data.path("location").isObject() || data.path("location").isEmpty()) throw new InvalidEventException(); }
}
