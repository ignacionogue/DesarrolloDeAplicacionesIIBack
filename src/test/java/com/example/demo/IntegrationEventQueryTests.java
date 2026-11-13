package com.example.demo;

import com.example.demo.infrastructure.integration.JsonSql;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IntegrationEventQueryTests {
    @LocalServerPort int port;
    @Autowired TestUsers users;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonSql jsonSql;

    @BeforeEach void seedEvents() {
        jdbc.update("DELETE FROM event_outbox");
        jdbc.update("DELETE FROM event_inbox");
        insertIncoming(OffsetDateTime.parse("2026-11-12T10:00:00Z"), "incoming-old");
        insertOutgoing(OffsetDateTime.parse("2026-11-12T11:00:00Z"), "outgoing-old");
        insertIncoming(OffsetDateTime.parse("2026-11-12T12:00:00Z"), "incoming-new");
        insertOutgoing(OffsetDateTime.parse("2026-11-12T13:00:00Z"), "outgoing-new");
    }

    @Test void endpointMatchesFrontendPageContractAndSupportsFiltersCountAndPagination() throws Exception {
        assertPage("ALL", 0, 2, 4, 2, "outgoing-new", "incoming-new");
        assertPage("ALL", 1, 2, 4, 2, "outgoing-old", "incoming-old");
        assertPage("INCOMING", 0, 1, 2, 2, "incoming-new");
        assertPage("OUTGOING", 0, 2, 2, 1, "outgoing-new", "outgoing-old");
    }

    private void assertPage(String direction, int pageNumber, int size, int total, int totalPages,
                            String... expectedTypes) throws Exception {
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(
                        "http://localhost:" + port + "/api/public-works/integration-events?page=" + pageNumber
                                + "&size=" + size + "&direction=" + direction))
                .header("Authorization", "Bearer " + users.issueToken("integration-query", "PERSONAL_OBRAS"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        var result = json.readTree(response.body());
        assertEquals(pageNumber, result.path("page").asInt());
        assertEquals(size, result.path("size").asInt());
        assertEquals(total, result.path("totalElements").asInt());
        assertEquals(totalPages, result.path("totalPages").asInt());
        var actualTypes = new java.util.ArrayList<String>();
        result.path("content").forEach(event -> actualTypes.add(event.path("eventType").asText()));
        assertEquals(java.util.List.of(expectedTypes), actualTypes);
        for (var event : result.path("content")) {
            assertTrue(event.path("direction").asText().matches("INCOMING|OUTGOING"));
            assertTrue(event.has("id"));
            assertTrue(event.has("status"));
        }
    }

    private void insertIncoming(OffsetDateTime occurredAt, String eventType) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO event_inbox(event_id,event_type,event_version,source_module,occurred_at,payload,envelope,status) " +
                        "VALUES (?,?,?,?,?,CAST(? AS JSONB),CAST(? AS JSONB),?)", id, eventType, "1.0", "TEST", occurredAt,
                jsonSql.parameter("{}"), jsonSql.parameter("{}"), "RECEIVED");
    }

    private void insertOutgoing(OffsetDateTime createdAt, String eventType) {
        UUID id = UUID.randomUUID();
        String envelope = "{\"eventType\":\"" + eventType + "\",\"sourceModule\":\"TEST\"}";
        jdbc.update("INSERT INTO event_outbox(id,event_id,ordering_key,envelope,payload,status,created_at) " +
                        "VALUES (?,?,?,CAST(? AS JSONB),CAST(? AS JSONB),?,?)", UUID.randomUUID(), id, "test:" + id,
                jsonSql.parameter(envelope), jsonSql.parameter("{}"), "PENDING", createdAt);
    }
}
