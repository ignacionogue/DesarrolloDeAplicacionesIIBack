package com.example.demo.application.events;

import org.springframework.stereotype.Component;
import tools.jackson.databind.node.ObjectNode;

import java.util.Set;
import java.util.UUID;

@Component
public class CaseFileResolvedHandler implements EventHandler {
    private static final Set<String> RESOLUTIONS = Set.of("approved", "rejected");
    private final ProjectResolutionPort projects;
    private final tools.jackson.databind.ObjectMapper json;

    public CaseFileResolvedHandler(ProjectResolutionPort projects, tools.jackson.databind.ObjectMapper json) {
        this.projects = projects;
        this.json = json;
    }

    @Override public String eventType() { return "caseFileResolved"; }
    @Override public String sourceModule() { return "ciudadanos"; }
    @Override public boolean relevant(EventEnvelope event) {
        return "PUBLIC_WORKS_PROJECT".equals(event.data().path("externalReference").path("type").asString());
    }
    @Override public void validate(EventEnvelope event) {
        var data = event.data();
        EnvironmentEventFields.uuid(data, "caseFileId");
        EnvironmentEventFields.text(data, "caseFileNumber");
        EnvironmentEventFields.text(data, "state");
        EnvironmentEventFields.text(data, "resolution");
        if (!"resolved".equals(data.path("state").asString())
                || !RESOLUTIONS.contains(data.path("resolution").asString())) throw new InvalidEventException();
        var reference = data.path("externalReference");
        if (!reference.isObject() || !"PUBLIC_WORKS_PROJECT".equals(reference.path("type").asString()))
            throw new InvalidEventException();
        EnvironmentEventFields.uuid(reference, "id");
        if (!projects.supports(event.eventType(), event.eventVersion())) throw new InvalidEventException();
    }
    @Override public EventEnvelope minimize(EventEnvelope event) {
        var original = event.data();
        ObjectNode data = json.createObjectNode();
        data.put("caseFileId", UUID.fromString(original.path("caseFileId").asString()).toString());
        data.put("caseFileNumber", original.path("caseFileNumber").asString());
        data.put("state", original.path("state").asString());
        data.put("resolution", original.path("resolution").asString());
        var reference = data.putObject("externalReference");
        reference.put("type", "PUBLIC_WORKS_PROJECT");
        reference.put("id", original.path("externalReference").path("id").asString());
        return new EventEnvelope(event.eventId(), event.eventType(), event.eventVersion(), event.occurredAt(),
                event.sourceModule(), event.correlationId(), event.causationId(), data);
    }
    @Override public void handle(EventEnvelope event) { projects.apply(event); }
}
