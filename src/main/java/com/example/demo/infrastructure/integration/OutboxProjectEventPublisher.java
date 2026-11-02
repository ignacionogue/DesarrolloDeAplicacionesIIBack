package com.example.demo.infrastructure.integration;

import com.example.demo.application.events.EventEnvelope;
import com.example.demo.application.events.EventOutbox;
import com.example.demo.application.events.OutgoingEventValidator;
import com.example.demo.integration.ProjectEventPublisher;
import com.example.demo.model.ProyectoObra;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Component
public class OutboxProjectEventPublisher implements ProjectEventPublisher {
    private final ObjectMapper json;
    private final EventOutbox outbox;
    private final OutgoingEventValidator schemas;

    public OutboxProjectEventPublisher(ObjectMapper json, EventOutbox outbox, OutgoingEventValidator schemas) {
        this.json = json;
        this.outbox = outbox;
        this.schemas = schemas;
    }

    @Override public void publishProjectApproved(ProyectoObra project) {
        var data = common(project);
        data.put("name", project.getName());
        data.put("approvedAt", project.getApprovedAt().atStartOfDay().atOffset(ZoneOffset.UTC)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")));
        if (project.getApprovedBudget() != null) data.put("approvedBudget", project.getApprovedBudget());
        append("publicWorksProjectApproved", project, data);
    }

    @Override public void publishProjectCompleted(ProyectoObra project) {
        var data = common(project);
        data.put("completedAt", OffsetDateTime.now(ZoneOffset.UTC).toString());
        if (project.getUsedBudget() != null) data.put("finalBudget", project.getUsedBudget());
        if (project.getCaseFileId() != null) data.put("caseFileId", project.getCaseFileId().toString());
        append("publicWorksProjectCompleted", project, data);
    }

    private tools.jackson.databind.node.ObjectNode common(ProyectoObra project) {
        var data = json.createObjectNode();
        data.put("publicWorksProjectId", project.getIntegrationId().toString());
        data.put("projectNumber", "OBRA-" + project.getId());
        return data;
    }

    private void append(String type, ProyectoObra project, tools.jackson.databind.node.ObjectNode data) {
        var event = EventEnvelope.outgoing(type, data, project.getIntegrationCorrelationId(), null);
        schemas.validate(event);
        outbox.append(event, "project:" + project.getIntegrationId());
    }
}
