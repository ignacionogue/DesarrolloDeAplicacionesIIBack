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
    static void location(JsonNode data) {
        var location=data.path("location");
        if(!location.isObject()) throw new InvalidEventException();
        var allowed=java.util.Set.of("street","number","neighborhoodId","lat","lng");
        for(String name:location.propertyNames()) if(!allowed.contains(name)) throw new InvalidEventException();
        for(String field:java.util.List.of("street","number"))
            if(location.hasNonNull(field) && !location.path(field).isString()) throw new InvalidEventException();
        if(location.hasNonNull("neighborhoodId")) uuid(location,"neighborhoodId");
        for(String field:java.util.List.of("lat","lng"))
            if(location.hasNonNull(field) && !location.path(field).isNumber()) throw new InvalidEventException();
    }
}
