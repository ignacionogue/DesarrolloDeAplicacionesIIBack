package com.example.demo.application.events;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.ObjectNode;

@Component
public class RoadAccidentRegisteredHandler implements EventHandler {
    private final RoadAccidentStore store;
    private final boolean strictUuid;
    public RoadAccidentRegisteredHandler(RoadAccidentStore store,@Value("${app.messaging.m7-accident-uuid-required:false}") boolean strictUuid) {
        this.store=store;this.strictUuid=strictUuid;
    }
    public String eventType() {return "roadAccidentRegistered";}
    public String sourceModule() {return "transito";}
    public boolean relevant(EventEnvelope e) {return e.data().path("involvesInfrastructureDamage").asBoolean();}
    public void validate(EventEnvelope e) {
        var d=e.data(); EnvironmentEventFields.bool(d,"involvesInfrastructureDamage");
        if(!relevant(e)) return;
        EnvironmentEventFields.text(d,"accidentId");
        if(d.path("accidentId").asString().length()>180) throw new InvalidEventException();
        if(strictUuid) EnvironmentEventFields.uuid(d,"accidentId");
        EnvironmentEventFields.time(d,"accidentDateTime"); EnvironmentEventFields.text(d,"damageDescription"); EnvironmentEventFields.location(d);
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
