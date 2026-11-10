package com.example.demo.application.events;

import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class ClosureResponseProcessor {
    private final ClosureIntegrationStore closures;
    public ClosureResponseProcessor(ClosureIntegrationStore closures) {this.closures=closures;}
    public void apply(EventEnvelope event) {
        var id=UUID.fromString(event.data().path("closureRequestId").asString());
        // A response preceding a local request remains retryable in the Inbox.
        var existing=closures.lock(id).orElseThrow(()->new IllegalStateException("CLOSURE_REQUEST_NOT_FOUND"));
        if(existing.state().equals("ENDED")) return;
        if(!event.eventType().equals("streetClosureEnded") && existing.lastEventAt()!=null && !event.occurredAt().isAfter(existing.lastEventAt())) return;
        if(event.correlationId()!=null && !event.correlationId().equals(existing.correlationId())) throw new RejectedEventException();
        if(event.causationId()!=null && !event.causationId().equals(existing.requestEventId())) throw new RejectedEventException();
        if(existing.authorization()!=null && existing.authorization().hasNonNull("streetClosureId")
                && event.data().hasNonNull("streetClosureId")
                && !existing.authorization().path("streetClosureId").equals(event.data().path("streetClosureId")))
            throw new RejectedEventException();
        closures.apply(existing,event);
    }
}
