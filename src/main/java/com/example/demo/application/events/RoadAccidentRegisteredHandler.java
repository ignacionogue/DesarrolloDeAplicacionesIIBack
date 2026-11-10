package com.example.demo.application.events;

import org.springframework.stereotype.Component;
import tools.jackson.databind.node.ObjectNode;

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
        EnvironmentEventFields.time(d,"accidentDateTime"); EnvironmentEventFields.text(d,"damageDescription"); EnvironmentEventFields.location(d);
        if(d.hasNonNull("inspectorId")) EnvironmentEventFields.uuid(d,"inspectorId");
        if(e.correlationId()==null) throw new InvalidEventException();
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
