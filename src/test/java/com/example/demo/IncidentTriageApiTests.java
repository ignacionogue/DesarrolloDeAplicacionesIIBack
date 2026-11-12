package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.ai.triage-enabled=true", "app.ai.rate-limit.max-requests=2"
})
class IncidentTriageApiTests {
    @LocalServerPort int port;
    @Autowired TestUsers users;

    @Test
    void onlyApprovedRolesCanRequestSuggestionsAndNoActionIsExecuted() throws Exception {
        assertEquals(401, call(null, "Bache profundo frente a escuela").statusCode());
        assertEquals(403, call(users.issueToken("operator", "OPERARIO_CONTRATISTA"),
                "Bache profundo frente a escuela").statusCode());
        var response = call(users.issueToken("personal", "PERSONAL_OBRAS"),
                "Bache profundo frente a escuela");
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains("\"humanReviewRequired\":true"));
        assertTrue(response.headers().firstValue("X-AI-Provider").orElse("").equals("mock-v1"));
    }

    @Test
    void endpointAppliesPerUserRateLimit() throws Exception {
        String token = users.issueToken("rate-user", "INGENIERO_ARQUITECTO");
        assertEquals(200, call(token, "Vereda rota").statusCode());
        assertEquals(200, call(token, "Luminaria apagada").statusCode());
        var limited = call(token, "Sumidero tapado");
        assertEquals(429, limited.statusCode());
        assertEquals("60", limited.headers().firstValue("Retry-After").orElse(""));
    }

    private HttpResponse<String> call(String token, String description) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port
                        + "/api/public-works/assistant/triage"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"description\":\"" + description + "\"}"));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
