package com.example.demo.application.events;
import org.springframework.stereotype.Component;
import java.time.OffsetDateTime;

@Component
public class StreetClosureApprovedHandler implements EventHandler {
    private final ClosureResponseProcessor processor;
    public StreetClosureApprovedHandler(ClosureResponseProcessor processor) {this.processor=processor;}
    public String eventType() {return "streetClosureApproved";}
    public String sourceModule() {return "transito";}
    public boolean relevant(EventEnvelope e) {return ClosureEventValidation.relevant(e);}
    public void validate(EventEnvelope e) {
        ClosureEventValidation.responseToRequest(e); var d=e.data(); EnvironmentEventFields.uuid(d,"streetClosureId");
        EnvironmentEventFields.text(d,"requestingModule"); EnvironmentEventFields.time(d,"startDate"); EnvironmentEventFields.time(d,"endDate");
        if(!OffsetDateTime.parse(d.path("endDate").asString()).isAfter(OffsetDateTime.parse(d.path("startDate").asString()))) throw new InvalidEventException();
        ClosureEventValidation.sections(d.path("affectedSections"));
        if(d.has("conditions")) {if(!d.path("conditions").isArray()) throw new InvalidEventException(); for(var c:d.path("conditions")) if(!c.isString()) throw new InvalidEventException();}
        if(d.has("detours")) {if(!d.path("detours").isArray()) throw new InvalidEventException(); for(var c:d.path("detours")) {EnvironmentEventFields.text(c,"street");EnvironmentEventFields.text(c,"alternateRoute");}}
    }
    public void handle(EventEnvelope e) {processor.apply(e);}
}
