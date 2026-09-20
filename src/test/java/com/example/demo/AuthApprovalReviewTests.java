package com.example.demo;

import com.example.demo.model.ProyectoObra;
import com.example.demo.repository.ProyectoObraRepository;
import com.example.demo.security.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.Date;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

/** Regression coverage for authentication and the project approval contract. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.auth.role=PERSONAL_OBRAS"})
class AuthApprovalReviewTests {
    @LocalServerPort int port;
    @Autowired JwtService jwt;
    @Autowired ProyectoObraRepository projects;
    @Autowired tools.jackson.databind.ObjectMapper json;
    private HttpResponse<String> call(String method, String path, String body, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return HttpClient.newHttpClient().send(builder.method(method, body == null ?
                HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
    private Long pending() {
        var p = new ProyectoObra("Review", null, null, null, new BigDecimal("1000"), null,
                LocalDate.now(), 30, null, null, null);
        p.marcarPendienteAprobacion();
        return projects.save(p).getId();
    }
    private String approval(String observations) {
        return "{\"approvedBudget\":900,\"approvedDeadlineDays\":25,\"approvedAt\":\"2026-09-19\",\"observations\":\"" + observations + "\"}";
    }
    @Test void validApprovalPersistsNewFields() throws Exception {
        var id = pending();
        var r = call("PATCH", "/api/public-works/projects/" + id + "/approve", approval("Conforme"), jwt.issueToken("test.user", "RESPONSABLE_AUTORIZADO"));
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"status\":\"APROBADO\""));
        var p = projects.findById(id).orElseThrow();
        assertEquals(LocalDate.of(2026, 9, 19), p.getApprovedAt());
        assertEquals("Conforme", p.getApprovalObservations());
        assertEquals(25, p.getApprovedDeadlineDays());
        for (String path : new String[]{"/api/public-works/projects/" + id, "/api/public-works/projects?search=Review&size=100"}) {
            var response = call("GET", path, null, jwt.issueToken("reader", "PERSONAL_OBRAS"));
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"approvedAt\":\"2026-09-19\""));
            assertTrue(response.body().contains("\"approvalObservations\":\"Conforme\""));
        }
        assertEquals(409, call("PATCH", "/api/public-works/projects/" + id + "/approve", approval("Otra"),
                jwt.issueToken("approver", "RESPONSABLE_AUTORIZADO")).statusCode());
    }
    @Test void missingAndTamperedTokensAreRejected() throws Exception {
        assertEquals(401, call("GET", "/api/public-works/projects", null, null).statusCode());
        assertEquals(401, call("GET", "/api/public-works/projects", null, "invalid.token.signature").statusCode());
    }
    @Test void expiredTokenIsRejected() throws Exception {
        String expired = Jwts.builder().subject("test.user").claim("role", "RESPONSABLE_AUTORIZADO")
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor("test-secret-for-jwt-signing-with-at-least-32-bytes".getBytes(StandardCharsets.UTF_8))).compact();
        assertEquals(401, call("GET", "/api/public-works/projects", null, expired).statusCode());
    }
    @Test void loginFailureUsesApiErrorContract() throws Exception {
        var r = call("POST", "/api/auth/login", "{\"username\":\"test.user\",\"password\":\"wrong\"}", null);
        assertEquals(401, r.statusCode());
        assertTrue(r.body().contains("\"code\""), "401 must include consistent API error body");
    }
    @Test void nonApproverRoleCannotApprove() throws Exception {
        var login = call("POST", "/api/auth/login", "{\"username\":\"test.user\",\"password\":\"test-password\"}", null);
        assertEquals(200, login.statusCode());
        String token = login.body().replaceFirst(".*\\\"accessToken\\\":\\\"([^\\\"]+)\\\".*", "$1");
        assertEquals(403, call("PATCH", "/api/public-works/projects/" + pending() + "/approve", approval(""), token).statusCode());
    }
    @Test void oversizedObservationsAreValidationErrors() throws Exception {
        assertEquals(400, call("PATCH", "/api/public-works/projects/" + pending() + "/approve",
                approval("x".repeat(1001)), jwt.issueToken("test.user", "RESPONSABLE_AUTORIZADO")).statusCode());
    }
    @Test void openApiDocumentsBearerAuthentication() throws Exception {
        var response = call("GET", "/v3/api-docs", null, null);
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"securitySchemes\""), "Swagger must describe required bearer authentication");
        var document = json.readTree(response.body());
        assertTrue(document.at("/paths/~1api~1public-works~1work-orders/get/security").isArray());
        assertTrue(document.at("/paths/~1api~1auth~1login/post/security").isMissingNode());
    }

    @Test void invalidApprovalDataIs400AndDoesNotChangeProject() throws Exception {
        var id = pending();
        var token = jwt.issueToken("approver", "RESPONSABLE_AUTORIZADO");
        var valid = approval("");
        for (String body : new String[]{"{}", "null", "{", valid.replace("900", "0"), valid.replace("900", "-1"),
                valid.replace("900", "10000000000000"), valid.replace("900", "1.001"),
                valid.replace("25", "0"), valid.replace("25", "-1"),
                valid.replace("\"approvedBudget\":900,", ""), valid.replace("\"approvedDeadlineDays\":25,", ""),
                valid.replace("\"approvedAt\":\"2026-09-19\",", ""), valid.replace("2026-09-19", "2026-02-30")}) {
            var response = call("PATCH", "/api/public-works/projects/" + id + "/approve", body, token);
            assertEquals(400, response.statusCode(), body + " " + response.body());
            assertEquals("VALIDATION_ERROR", json.readTree(response.body()).get("code").asText());
            assertTrue(json.readTree(response.body()).get("details").isArray());
        }
        assertNull(projects.findById(id).orElseThrow().getApprovedAt());
        assertEquals(com.example.demo.model.EstadoAprobacion.PENDIENTE_APROBACION, projects.findById(id).orElseThrow().getApprovalStatus());
    }

    @Test void projectCanBeCreatedSubmittedAndRejectedByCorrectRoles() throws Exception {
        var personal = jwt.issueToken("personal", "PERSONAL_OBRAS");
        var approver = jwt.issueToken("approver", "RESPONSABLE_AUTORIZADO");
        var created = call("POST", "/api/public-works/projects", "{\"name\":\"Review flow\",\"estimatedBudget\":1000,\"estimatedStartDate\":\"2026-09-22\",\"estimatedDurationDays\":30}", personal);
        assertEquals(201, created.statusCode(), created.body());
        var path = "/api/public-works/projects/" + json.readTree(created.body()).get("id").asLong();
        assertEquals(409, call("PATCH", path + "/approve", approval(""), approver).statusCode());
        assertEquals(200, call("PATCH", path + "/submit-approval", null, personal).statusCode());
        assertEquals(409, call("PATCH", path + "/submit-approval", null, personal).statusCode());
        assertEquals(200, call("PATCH", path + "/reject", null, approver).statusCode());
        assertEquals(409, call("PATCH", path + "/approve", approval(""), approver).statusCode());
    }

    @Test void generalEditCannotOverwriteOrEraseApprovedTerms() throws Exception {
        var id = pending();
        var path = "/api/public-works/projects/" + id;
        assertEquals(200, call("PATCH", path + "/approve", approval("Conforme"), jwt.issueToken("approver", "RESPONSABLE_AUTORIZADO")).statusCode());
        var body = "{\"name\":\"Review updated\",\"estimatedBudget\":1000,\"estimatedStartDate\":\"2026-09-22\",\"estimatedDurationDays\":30}";
        var token = jwt.issueToken("personal", "PERSONAL_OBRAS");
        assertEquals(422, call("PUT", path, body.replace("}", ",\"approvedBudget\":100}"), token).statusCode());
        assertEquals(422, call("PUT", path, body.replace("}", ",\"approvedDeadlineDays\":1}"), token).statusCode());
        assertEquals(200, call("PUT", path, body, token).statusCode());
        var saved = projects.findById(id).orElseThrow();
        assertEquals(0, BigDecimal.valueOf(900).compareTo(saved.getApprovedBudget()));
        assertEquals(25, saved.getApprovedDeadlineDays());
        assertEquals("Conforme", saved.getApprovalObservations());
    }

    @Test void unknownRolesAndTokensWithoutExpirationAreRejected() throws Exception {
        assertEquals(401, call("GET", "/api/public-works/projects", null, jwt.issueToken("unknown", "ADMIN")).statusCode());
        var token = Jwts.builder().subject("user").claim("role", "PERSONAL_OBRAS")
                .signWith(Keys.hmacShaKeyFor("test-secret-for-jwt-signing-with-at-least-32-bytes".getBytes(StandardCharsets.UTF_8))).compact();
        var response = call("GET", "/api/public-works/projects", null, token);
        assertEquals(401, response.statusCode());
        assertEquals("UNAUTHORIZED", json.readTree(response.body()).get("code").asText());
    }
}
