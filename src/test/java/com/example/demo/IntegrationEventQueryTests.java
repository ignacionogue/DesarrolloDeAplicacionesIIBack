package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IntegrationEventQueryTests {
    @LocalServerPort int port;
    @Autowired TestUsers users;
    @Autowired ObjectMapper json;

    @Test void endpointMatchesFrontendPageContractAndSupportsDirectionFilters() throws Exception {
        for (String direction : new String[]{"ALL", "INCOMING", "OUTGOING"}) {
            var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(
                            "http://localhost:" + port + "/api/public-works/integration-events?page=0&size=20&direction=" + direction))
                    .header("Authorization", "Bearer " + users.issueToken("integration-query", "PERSONAL_OBRAS"))
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), response.body());
            var page = json.readTree(response.body());
            assertTrue(page.path("content").isArray());
            assertEquals(0, page.path("page").asInt());
            assertEquals(20, page.path("size").asInt());
            assertTrue(page.has("totalElements"));
            assertTrue(page.has("totalPages"));
            for (var event : page.path("content")) {
                assertTrue(event.path("direction").asText().matches("INCOMING|OUTGOING"));
                assertTrue(event.has("id"));
                assertTrue(event.has("status"));
            }
        }
    }
}
