package com.example.demo.application.events;

import org.springframework.stereotype.Service;

@Service
public class EventDispatcher {
    private final EventHandlerRegistry registry;
    private final EventInbox inbox;
    private final EventSerialization codec;
    private final EventTelemetry metrics;
    private final TicketEventValidation m2;
    public EventDispatcher(EventHandlerRegistry registry, EventInbox inbox, EventSerialization codec, EventTelemetry metrics, TicketEventValidation m2) {
        this.registry=registry; this.inbox=inbox; this.codec=codec; this.metrics=metrics; this.m2=m2;
    }
    public String dispatch(byte[] bytes) {
        var event=codec.decode(bytes);
        metrics.result("received",event);
        if (event.eventType().equals("ticketCreated")) {
            m2.validate(event); metrics.result("ignored",event); return "ignored";
        }
        var match=registry.find(event);
        if (match.isEmpty()) {
            // Pending contracts must not be silently ACKed as if their business work had completed.
            if (java.util.Set.of("infrastructureRepairRequested","containerDamaged","treeRiskDetected",
                    "streetClosureApproved","streetClosureRejected","streetClosureEnded","roadAccidentRegistered")
                    .contains(event.eventType())) throw new IllegalStateException("HANDLER_NOT_ENABLED");
            metrics.result("ignored",event); return "ignored";
        }
        var handler=match.get();
        handler.validate(event);
        if (!handler.relevant(event)) { metrics.result("ignored",event); return "ignored"; }
        inbox.receive(handler.minimize(event));
        String result=inbox.process(event.eventId(),registry);
        metrics.result(result,event);
        return result;
    }
    public void recover() {
        for (var id : inbox.pending()) {
            try { metrics.result(inbox.process(id,registry),null); }
            catch (RuntimeException ex) { metrics.result("error",null); }
        }
    }
}
