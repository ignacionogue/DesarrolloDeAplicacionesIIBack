package com.example.demo.infrastructure.integration;

import com.example.demo.application.events.*;
import com.example.demo.model.OrdenTrabajo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class OutboxEnvironmentEventPublisher implements EnvironmentEventPublisher {
    private final boolean enabled;
    private final ObjectMapper json;
    private final EventOutbox outbox;
    private final M3SchemaValidator schemas;
    public OutboxEnvironmentEventPublisher(@Value("${app.messaging.m6-uuid-contract-confirmed:false}") boolean enabled,
            ObjectMapper json,EventOutbox outbox,M3SchemaValidator schemas) {
        this.enabled=enabled; this.json=json; this.outbox=outbox; this.schemas=schemas;
    }
    public void publish(OrdenTrabajo order,EnvironmentCaseStore.Case source,WorkOrderEventHandler.Action action) {
        if(!enabled) return; // v0.6 explicitly requires bilateral acceptance of technical UUIDs.
        var data=json.createObjectNode();
        data.put("sourceRequestId",source.sourceId().toString()); data.put("workOrderId",order.getIntegrationId().toString());
        String type;
        if(action==WorkOrderEventHandler.Action.SCHEDULE) {
            type="workOrderScheduled";
            if(order.getEstimatedDurationHours()!=null) data.put("estimatedDuration",java.time.Duration.ofHours(order.getEstimatedDurationHours()).toString());
        } else {
            type="workOrderCompleted"; data.put("completedAt",java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).toString());
            if(order.getOutcome()!=null) data.put("outcome",order.getOutcome());
        }
        var event=EventEnvelope.outgoing(type,data,source.correlationId(),null);
        schemas.validate(event); outbox.append(event,"environment:"+source.sourceId());
    }
}
