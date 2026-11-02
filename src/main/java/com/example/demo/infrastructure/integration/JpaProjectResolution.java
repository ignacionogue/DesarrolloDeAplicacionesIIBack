package com.example.demo.infrastructure.integration;

import com.example.demo.application.events.EventEnvelope;
import com.example.demo.application.events.ProjectResolutionPort;
import com.example.demo.application.events.RejectedEventException;
import com.example.demo.repository.ProyectoObraRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class JpaProjectResolution implements ProjectResolutionPort {
    private final ProyectoObraRepository projects;

    public JpaProjectResolution(ProyectoObraRepository projects) { this.projects = projects; }

    @Override public boolean supports(String eventType, String eventVersion) {
        return "caseFileResolved".equals(eventType) && "1.0".equals(eventVersion);
    }

    @Override public void apply(EventEnvelope resolution) {
        var data = resolution.data();
        UUID publicProjectId = UUID.fromString(data.path("externalReference").path("id").asString());
        var project = projects.findLockedByIntegrationId(publicProjectId).orElseThrow(RejectedEventException::new);
        project.registrarResolucionExpediente(
                UUID.fromString(data.path("caseFileId").asString()),
                data.path("caseFileNumber").asString(),
                data.path("resolution").asString(),
                resolution.occurredAt());
        projects.save(project);
    }
}
