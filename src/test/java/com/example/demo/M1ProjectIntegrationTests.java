package com.example.demo;

import com.example.demo.application.events.EventDispatcher;
import com.example.demo.dto.request.ProjectApprovalRequest;
import com.example.demo.dto.request.ProyectoObraRequest;
import com.example.demo.service.ProyectoObraService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class M1ProjectIntegrationTests {
    @Autowired ProyectoObraService projects;
    @Autowired EventDispatcher dispatcher;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    @Test void approvalPublishesInstitutionalProjectAndResolutionLinksByPublicUuid() {
        var request = new ProyectoObraRequest();
        request.setName("Obra institucional " + UUID.randomUUID());
        request.setEstimatedBudget(new BigDecimal("25000000"));
        request.setEstimatedStartDate(LocalDate.of(2026, 11, 10));
        request.setEstimatedDurationDays(90);
        request.setPhysicalProgress(0);
        var created = projects.crear(request);
        projects.enviarAAprobacion(created.id());
        projects.aprobar(created.id(), new ProjectApprovalRequest(new BigDecimal("24000000"), 90,
                LocalDate.of(2026, 11, 1), "Aprobada para expediente institucional"));

        var row = jdbc.queryForMap("SELECT integration_id,integration_correlation_id FROM proyecto_obra WHERE id=?", created.id());
        UUID projectId = (UUID) row.get("integration_id");
        var envelope = json.readTree(jdbc.queryForObject(
                "SELECT envelope FROM event_outbox WHERE ordering_key=?", String.class, "project:" + projectId));
        assertEquals("publicWorksProjectApproved", envelope.path("eventType").asString());
        assertEquals(projectId.toString(), envelope.path("data").path("publicWorksProjectId").asString());
        assertEquals(row.get("integration_correlation_id").toString(), envelope.path("correlationId").asString());
        assertFalse(envelope.path("data").has("citizenId"));
        assertFalse(envelope.path("data").has("caseFileId"));

        UUID caseFileId = UUID.randomUUID();
        var event = json.createObjectNode();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventType", "caseFileResolved");
        event.put("eventVersion", "1.0");
        event.put("occurredAt", "2026-11-01T10:07:00-03:00");
        event.put("sourceModule", "ciudadanos");
        event.put("correlationId", envelope.path("correlationId").asString());
        var data = event.putObject("data");
        data.put("caseFileId", caseFileId.toString());
        data.put("caseFileNumber", "EXP-2026-001042");
        data.put("state", "resolved");
        data.put("resolution", "approved");
        data.put("citizenId", UUID.randomUUID().toString());
        data.putObject("externalReference").put("type", "PUBLIC_WORKS_PROJECT").put("id", projectId.toString());

        byte[] bytes = event.toString().getBytes(StandardCharsets.UTF_8);
        assertEquals("processed", dispatcher.dispatch(bytes));
        assertEquals("duplicated", dispatcher.dispatch(bytes));
        var linked = jdbc.queryForMap("SELECT case_file_id,case_file_number,case_file_resolution FROM proyecto_obra WHERE id=?", created.id());
        assertEquals(caseFileId, linked.get("case_file_id"));
        assertEquals("EXP-2026-001042", linked.get("case_file_number"));
        assertEquals("approved", linked.get("case_file_resolution"));
        String persisted = jdbc.queryForObject("SELECT payload FROM event_inbox WHERE event_id=?", String.class,
                UUID.fromString(event.path("eventId").asString()));
        assertFalse(persisted.contains("citizenId"));
    }

    @Test void rejectsResolutionForUnknownProjectAsTerminalBusinessRejection() {
        var event = json.createObjectNode();
        event.put("eventId", UUID.randomUUID().toString()).put("eventType", "caseFileResolved")
                .put("eventVersion", "1.0").put("occurredAt", "2026-11-01T10:07:00-03:00")
                .put("sourceModule", "ciudadanos").put("correlationId", UUID.randomUUID().toString());
        var data = event.putObject("data");
        data.put("caseFileId", UUID.randomUUID().toString()).put("caseFileNumber", "EXP-UNKNOWN")
                .put("state", "resolved").put("resolution", "approved");
        data.putObject("externalReference").put("type", "PUBLIC_WORKS_PROJECT").put("id", UUID.randomUUID().toString());
        assertEquals("rejected", dispatcher.dispatch(event.toString().getBytes(StandardCharsets.UTF_8)));
    }
}
