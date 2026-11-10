package com.example.demo.infrastructure.integration;

import com.example.demo.application.events.*;
import com.example.demo.model.OrdenTrabajo;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class OutboxEnvironmentEventPublisher implements EnvironmentEventPublisher {
    private final ObjectMapper json;
    private final EventOutbox outbox;
    private final OutgoingEventValidator schemas;
    public OutboxEnvironmentEventPublisher(ObjectMapper json,EventOutbox outbox,OutgoingEventValidator schemas) {
        this.json=json; this.outbox=outbox; this.schemas=schemas;
    }
    public void publish(OrdenTrabajo order,EnvironmentCaseStore.Case source,WorkOrderEventHandler.Action action) {
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
