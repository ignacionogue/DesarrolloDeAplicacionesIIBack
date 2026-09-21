package com.example.demo;

import com.example.demo.model.*;
import com.example.demo.repository.*;
import com.example.demo.security.JwtService;
import com.example.demo.strategy.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WorkOrderFlowTests {
    private static final String BASE = "/api/public-works/work-orders";
    @LocalServerPort int port;
    @Autowired TestUsers jwt;
    @Autowired ObjectMapper json;
    @Autowired OrdenTrabajoRepository orders;
    @Autowired ProyectoObraRepository projects;
    @Autowired WorkOrderCreationStrategyResolver resolver;
    @Autowired com.example.demo.service.DashboardService dashboard;

    private HttpResponse<String> call(String method, String path, String body, String role) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json").header("Authorization", "Bearer " + jwt.issueToken("flow.fixture", role))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private long project() {
        return projects.save(new ProyectoObra("Proyecto strategy", null, null, null, BigDecimal.valueOf(1000), null,
                LocalDate.of(2026, 9, 22), 30, null, null, null)).getId();
    }

    private String body(String origin, Long projectId) {
        return "{\"origin\":\"" + origin + "\",\"description\":\"Prueba strategy\",\"priority\":\"ALTA\""
                + (projectId == null ? "" : ",\"projectId\":" + projectId) + "}";
    }

    private long create(String origin, Long projectId) throws Exception {
        var result = call("POST", BASE, body(origin, projectId), "PERSONAL_OBRAS");
        assertEquals(201, result.statusCode(), result.body());
        return json.readTree(result.body()).get("id").asLong();
    }

    @Test void manualOrderRemainsIndependentAndVisible() throws Exception {
        var id = create("MANUAL", null);
        var response = call("GET", BASE + "/" + id, null, "PERSONAL_OBRAS");
        var data = json.readTree(response.body());
        assertEquals("MANUAL", data.get("origin").asText());
        assertEquals("PENDIENTE", data.get("status").asText());
        assertTrue(data.get("projectId").isNull());
    }

    @Test void projectOrderPersistsRelationInDetailAndFilteredList() throws Exception {
        long externalBefore = dashboard.summary().externalWorkOrders();
        var projectId = project();
        var id = create("PROYECTO", projectId);
        assertEquals(externalBefore, dashboard.summary().externalWorkOrders());
        assertEquals(projectId, orders.findById(id).orElseThrow().getProject().getId());
        var detail = call("GET", BASE + "/" + id, null, "INSPECTOR_OBRA");
        assertEquals(projectId, json.readTree(detail.body()).get("projectId").asLong());
        var list = call("GET", BASE + "?origin=PROYECTO&size=100", null, "JEFE_CUADRILLA");
        assertEquals(200, list.statusCode());
        assertTrue(json.readTree(list.body()).get("content").valueStream()
                .anyMatch(row -> row.get("id").asLong() == id && row.get("projectId").asLong() == projectId));
    }

    @Test void invalidAssociationsDoNotPersist() throws Exception {
        long before = orders.count();
        for (String invalid : new String[]{body("PROYECTO", null), body("PROYECTO", 0L), body("PROYECTO", -1L),
                body("MANUAL", project()), body("INSPECCION", project()), body("DESCONOCIDO", null)}) {
            var response = call("POST", BASE, invalid, "PERSONAL_OBRAS");
            assertEquals(400, response.statusCode(), response.body());
            assertEquals("VALIDATION_ERROR", json.readTree(response.body()).get("code").asText());
        }
        var missing = call("POST", BASE, body("PROYECTO", 999999999L), "PERSONAL_OBRAS");
        assertEquals(404, missing.statusCode());
        assertEquals("NOT_FOUND", json.readTree(missing.body()).get("code").asText());
        assertEquals(before, orders.count());
    }

    @Test void updatesValidateAssociationAndKeepExecutionState() throws Exception {
        var projectId = project();
        var id = create("PROYECTO", projectId);
        var path = BASE + "/" + id;
        assertEquals(400, call("PUT", path, body("PROYECTO", null), "PERSONAL_OBRAS").statusCode());
        assertEquals(404, call("PUT", path, body("PROYECTO", 999999999L), "PERSONAL_OBRAS").statusCode());
        assertEquals(projectId, orders.findById(id).orElseThrow().getProject().getId());
        assertEquals(200, call("PATCH", path + "/schedule", "{\"scheduledDate\":\"2026-09-22\"}", "JEFE_CUADRILLA").statusCode());
        assertEquals(200, call("PATCH", path + "/start", null, "JEFE_CUADRILLA").statusCode());
        assertEquals(200, call("PUT", path, body("PROYECTO", projectId), "PERSONAL_OBRAS").statusCode());
        assertEquals(EstadoOT.EN_EJECUCION, orders.findById(id).orElseThrow().getStatus());
        assertEquals(projectId, orders.findById(id).orElseThrow().getProject().getId());
    }

    @ParameterizedTest
    @CsvSource({"MANUAL,ManualWorkOrderCreationStrategy", "PROYECTO,ProjectWorkOrderCreationStrategy",
            "ATENCION_CIUDADANA,ExistingOriginWorkOrderCreationStrategy", "INSPECCION,ExistingOriginWorkOrderCreationStrategy"})
    void resolverSelectsExactlyTheOriginStrategy(String origin, String type) {
        assertEquals(type, resolver.resolve(origin).getClass().getSimpleName());
        assertEquals(type, resolver.resolve(" " + origin.toLowerCase(java.util.Locale.ROOT) + " ").getClass().getSimpleName());
    }

    @Test void existingOriginsRemainCompatibleWithoutEventPayloads() throws Exception {
        create("ATENCION_CIUDADANA", null);
        create("INSPECCION", null);
    }

    @Test void fullWorkflowIncludesPauseRejectionReopeningAndValidation() throws Exception {
        var path = BASE + "/" + create("PROYECTO", project());
        assertEquals(409, call("PATCH", path + "/start", null, "JEFE_CUADRILLA").statusCode());
        assertEquals(409, call("PATCH", path + "/pause", null, "JEFE_CUADRILLA").statusCode());
        assertEquals(409, call("PATCH", path + "/complete", "{\"outcome\":\"OK\"}", "OPERARIO_CONTRATISTA").statusCode());
        assertEquals(409, call("PATCH", path + "/validate", "{\"approved\":true}", "INSPECTOR_OBRA").statusCode());
        transition(path, "schedule", "{\"scheduledDate\":\"2026-09-22\"}", "JEFE_CUADRILLA", "PROGRAMADA");
        transition(path, "start", null, "JEFE_CUADRILLA", "EN_EJECUCION");
        transition(path, "pause", null, "JEFE_CUADRILLA", "PAUSADA");
        transition(path, "start", null, "JEFE_CUADRILLA", "EN_EJECUCION");
        transition(path, "complete", "{\"outcome\":\"Reparado\"}", "OPERARIO_CONTRATISTA", "COMPLETADA");
        transition(path, "validate", "{\"approved\":false,\"observations\":\"Corregir terminacion\"}", "INSPECTOR_OBRA", "REABIERTA");
        transition(path, "start", null, "JEFE_CUADRILLA", "EN_EJECUCION");
        transition(path, "pause", null, "JEFE_CUADRILLA", "PAUSADA");
        transition(path, "complete", "{\"outcome\":\"Corregido\"}", "OPERARIO_CONTRATISTA", "COMPLETADA");
        transition(path, "validate", "{\"approved\":true}", "INSPECTOR_OBRA", "VALIDADA");
        assertEquals(409, call("PATCH", path + "/start", null, "JEFE_CUADRILLA").statusCode());
        assertEquals(409, call("PATCH", path + "/validate", "{\"approved\":true}", "INSPECTOR_OBRA").statusCode());
    }

    private void transition(String path, String action, String body, String role, String expected) throws Exception {
        var result = call("PATCH", path + "/" + action, body, role);
        assertEquals(200, result.statusCode(), result.body());
        assertEquals(expected, json.readTree(result.body()).get("status").asText());
    }

    @ParameterizedTest
    @CsvSource({
        "POST,/api/public-works/projects,PERSONAL_OBRAS", "PUT,/api/public-works/projects/1,PERSONAL_OBRAS",
        "PATCH,/api/public-works/projects/1/submit-approval,PERSONAL_OBRAS",
        "PATCH,/api/public-works/projects/1/approve,RESPONSABLE_AUTORIZADO",
        "PATCH,/api/public-works/projects/1/reject,RESPONSABLE_AUTORIZADO",
        "POST,/api/public-works/work-orders,PERSONAL_OBRAS", "PUT,/api/public-works/work-orders/1,PERSONAL_OBRAS",
        "PATCH,/api/public-works/work-orders/1/schedule,JEFE_CUADRILLA",
        "PATCH,/api/public-works/work-orders/1/start,JEFE_CUADRILLA",
        "PATCH,/api/public-works/work-orders/1/pause,JEFE_CUADRILLA",
        "PATCH,/api/public-works/work-orders/1/complete,OPERARIO_CONTRATISTA",
        "PATCH,/api/public-works/work-orders/1/validate,INSPECTOR_OBRA",
        "POST,/api/public-works/crews,PERSONAL_OBRAS", "POST,/api/public-works/street-closures,PERSONAL_OBRAS"
    })
    void everyMutationRejectsAllOtherRoles(String method, String path, String allowed) throws Exception {
        for (var role : JwtService.ROLES) {
            if (role.equals(allowed)) continue;
            var result = call(method, path, "{}", role);
            assertEquals(403, result.statusCode(), role + " " + path + " " + result.body());
            var error = json.readTree(result.body());
            assertEquals("FORBIDDEN", error.get("code").asText());
            assertTrue(error.get("details").isArray());
            assertFalse(error.get("message").asText().isBlank());
        }
    }
}
