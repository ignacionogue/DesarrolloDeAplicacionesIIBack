package com.example.demo.application.events;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Set;

@Component
public class RoadAccidentRegisteredHandler implements EventHandler {
    private final RoadAccidentStore store;
    public RoadAccidentRegisteredHandler(RoadAccidentStore store) {this.store=store;}
    public String eventType() {return "roadAccidentRegistered";}
    public String sourceModule() {return "transito";}
    public boolean relevant(EventEnvelope e) {return e.data().path("involvesInfrastructureDamage").asBoolean();}
    public void validate(EventEnvelope e) {
        var d=e.data(); EnvironmentEventFields.bool(d,"involvesInfrastructureDamage");
        if(!relevant(e)) return;
        EnvironmentEventFields.uuid(d,"accidentId");
        EnvironmentEventFields.time(d,"accidentDateTime"); EnvironmentEventFields.text(d,"damageDescription"); validateTrafficLocation(d);
        // M7 v1.0 exposes an opaque inspector reference (for example "u-1001" or "INS-124").
        // M3 does not correlate or persist it, so validate only that it is a non-empty string before minimization.
        if(d.hasNonNull("inspectorId")) EnvironmentEventFields.text(d,"inspectorId");
        if(e.correlationId()==null) throw new InvalidEventException();
    }
    private static void validateTrafficLocation(JsonNode data) {
        var location=data.path("location");
        if(!location.isObject()) throw new InvalidEventException();
        var allowed=Set.of("street","crossStreet","latitude","longitude");
        for(String name:location.propertyNames()) if(!allowed.contains(name)) throw new InvalidEventException();
        for(String field:java.util.List.of("street","crossStreet"))
            if(location.hasNonNull(field) && !location.path(field).isString()) throw new InvalidEventException();
        for(String field:java.util.List.of("latitude","longitude"))
            if(location.hasNonNull(field) && !location.path(field).isNumber()) throw new InvalidEventException();
    }
    public EventEnvelope minimize(EventEnvelope e) {
        var d=(ObjectNode)e.data();
        d.retain(java.util.List.of("accidentId","accidentNumber","accidentDateTime","involvesInfrastructureDamage","damageDescription","location"));
        var location=(ObjectNode)d.path("location"); location.retain(java.util.List.of("street","crossStreet","latitude","longitude"));
        // Vehicle/person/inspector information is unnecessary for infrastructure assessment.
        return new EventEnvelope(e.eventId(),e.eventType(),e.eventVersion(),e.occurredAt(),e.sourceModule(),e.correlationId(),e.causationId(),d);
    }
    public void handle(EventEnvelope e) {store.observe(e);}
}
