package com.example.demo.application.events;

import org.springframework.stereotype.Component;
import com.example.demo.application.events.TicketProjectionStore.Projection;
import java.time.OffsetDateTime;
import tools.jackson.databind.node.ObjectNode;

@Component
public class TicketUpdatedHandler implements EventHandler {
    private final TicketEventValidation validation;
    private final TicketProjectionStore store;
    public TicketUpdatedHandler(TicketEventValidation validation,TicketProjectionStore store) {this.validation=validation;this.store=store;}
    @Override public String eventType() {return "ticketUpdated";}
    @Override public String sourceModule() {return "atencion-ciudadana";}
    @Override public void validate(EventEnvelope event) {validation.validate(event);}
    @Override public boolean relevant(EventEnvelope event) {
        var data=event.data();
        if(!data.path("responsibleAreaId").asString().equals("M3")) return false;
        return !data.path("details").path("informationRequest").path("purpose").asString("").equals("RECLASSIFICATION")
                && !data.path("details").path("informationResponse").path("purpose").asString("").equals("RECLASSIFICATION");
    }
    @Override public EventEnvelope minimize(EventEnvelope event) {
        // Ownership identifiers from M1 are not needed for Obras' operational projection.
        var data=(ObjectNode)event.data(); data.remove("citizenId");
        return new EventEnvelope(event.eventId(),event.eventType(),event.eventVersion(),event.occurredAt(),event.sourceModule(),event.correlationId(),event.causationId(),data);
    }
    @Override public void handle(EventEnvelope event) {
        var p=store.lockOrCreate(event); var data=event.data();
        var time=OffsetDateTime.parse(data.path("updatedAt").asString());
        String type=data.path("updateType").asString();
        boolean latest=time.toInstant().isAfter(p.updatedAt().toInstant()) || (time.toInstant().equals(p.updatedAt().toInstant()) && data.equals(p.snapshot()));
        var snapshot=latest?data:p.snapshot();
        var routing=p.routing(); var routingAt=p.routingAt();
        var information=p.information(); var informationAt=p.informationAt();
        var escalation=p.escalation(); var escalationAt=p.escalationAt();
        boolean planning=p.planningReviewRequired(),evaluation=p.evaluationRequired();
        // ROUTED can arrive after a newer delta: hydrate the base without rolling state back.
        if(type.equals("ROUTED") && newer(time,routingAt)) {
            routing=data.path("details").path("routing"); routingAt=time;
            evaluation=true;
            if(routing.has("escalation") && newer(time,escalationAt)) {escalation=routing.get("escalation");escalationAt=time;}
        }
        if(type.equals("INFORMATION_PROVIDED") && newer(time,informationAt)) {
            information=data; informationAt=time; if(latest) evaluation=true;
        }
        if(type.equals("ESCALATION_CHANGED") && newer(time,escalationAt)) {
            escalation=data.path("details").path("escalation"); escalationAt=time;
        }
        if(latest && type.equals("PRIORITY_CHANGED")) planning=true;
        if(latest && type.equals("REOPENED")) evaluation=true;
        if(java.util.Set.of("CANCELLED", "RESOLVED", "CLOSED").contains(snapshot.path("currentStatus").asString())) {
            evaluation=false;
            planning=false;
        }
        store.save(new Projection(p.ticketId(),snapshot,latest?time:p.updatedAt(),routing,routingAt,information,informationAt,
                escalation,escalationAt,p.correlationId(),p.orderId(),p.started(),planning,evaluation));
    }
    private boolean newer(OffsetDateTime time,OffsetDateTime previous) {return previous==null || time.toInstant().isAfter(previous.toInstant());}
}
