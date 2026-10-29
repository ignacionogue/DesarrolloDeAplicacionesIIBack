package com.example.demo.application.events;
import org.springframework.stereotype.Component;

@Component
public class ContainerDamagedHandler implements EventHandler {
    private final EnvironmentCaseStore cases;
    public ContainerDamagedHandler(EnvironmentCaseStore cases) { this.cases=cases; }
    public String eventType() { return "containerDamaged"; }
    public String sourceModule() { return "ambiente"; }
    public boolean relevant(EventEnvelope event) { return event.data().path("requiresPublicWorks").asBoolean(); }
    public void validate(EventEnvelope event) {
        var d=event.data(); EnvironmentEventFields.bool(d,"requiresPublicWorks");
        EnvironmentEventFields.uuid(d,"containerId"); EnvironmentEventFields.uuid(d,"zoneId");
        EnvironmentEventFields.text(d,"containerCode"); EnvironmentEventFields.time(d,"detectedAt");
        EnvironmentEventFields.location(d);
        EnvironmentEventFields.text(d,"damageType"); EnvironmentEventFields.text(d,"severity");
        if(d.hasNonNull("ticketId")) EnvironmentEventFields.uuid(d,"ticketId");
    }
    public void handle(EventEnvelope event) { cases.receive(event,"containerId","detectedAt",true); }
}
