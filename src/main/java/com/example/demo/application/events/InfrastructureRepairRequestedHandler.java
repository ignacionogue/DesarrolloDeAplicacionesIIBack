package com.example.demo.application.events;

import org.springframework.stereotype.Component;

@Component
public class InfrastructureRepairRequestedHandler implements EventHandler {
    private final EnvironmentCaseStore cases;
    public InfrastructureRepairRequestedHandler(EnvironmentCaseStore cases) { this.cases=cases; }
    public String eventType() { return "infrastructureRepairRequested"; }
    public String sourceModule() { return "ambiente"; }
    public boolean relevant(EventEnvelope event) { return true; }
    public void validate(EventEnvelope event) {
        var d=event.data(); EnvironmentEventFields.uuid(d,"requestId");
        for(String field:java.util.List.of("damageType","severity","detectedIn")) EnvironmentEventFields.text(d,field);
        EnvironmentEventFields.location(d); EnvironmentEventFields.bool(d,"publicSafetyRisk");
        EnvironmentEventFields.time(d,"requestedAt");
        if(d.hasNonNull("ticketId")) EnvironmentEventFields.uuid(d,"ticketId");
    }
    public void handle(EventEnvelope event) { cases.receive(event,"requestId","requestedAt",true); }
}
