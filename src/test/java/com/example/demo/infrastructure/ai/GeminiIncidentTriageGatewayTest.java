package com.example.demo.infrastructure.ai;

import com.example.demo.application.ai.VersionedPromptRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class GeminiIncidentTriageGatewayTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void sendsApiKeyAndParsesStructuredResponse() throws Exception {
        var keyHeader = new AtomicReference<String>();
        var requestBody = new AtomicReference<String>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/models/gemini-test:generateContent", exchange -> {
            keyHeader.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = ("{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":"
                    + "\"{\\\"summary\\\":\\\"Bache con riesgo vial\\\",\\\"damageCategory\\\":\\\"PAVEMENT\\\","
                    + "\\\"suggestedPriority\\\":\\\"HIGH\\\",\\\"recommendedResourceType\\\":\\\"ROAD_CREW\\\","
                    + "\\\"confidence\\\":0.91,\\\"humanReviewRequired\\\":true,\\\"reasons\\\":[\\\"Riesgo frente a escuela\\\"]}\"}]}}]}"
            ).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        var gateway = gateway("test-secret");
        var suggestion = gateway.suggest("Bache profundo frente a una escuela", "incident-triage.v1");

        assertEquals("test-secret", keyHeader.get());
        assertTrue(requestBody.get().contains("responseJsonSchema"));
        assertTrue(requestBody.get().contains("MINIMAL"));
        assertTrue(requestBody.get().contains("Descripcion sanitizada"));
        assertFalse(requestBody.get().contains("test-secret"));
        assertEquals("PAVEMENT", suggestion.damageCategory().name());
        assertTrue(suggestion.humanReviewRequired());
    }

    @Test
    void rejectsProviderErrorsAndMissingSecret() throws Exception {
        assertThrows(IllegalStateException.class, () -> gateway(" "));
        assertThrows(IllegalStateException.class, () -> new GeminiIncidentTriageGateway(
                new ObjectMapper(), new VersionedPromptRepository(), "key", " ",
                "http://localhost:1", java.net.http.HttpClient.newHttpClient()));

        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/models/gemini-test:generateContent", exchange -> {
            byte[] response = "{\"error\":{\"message\":\"unavailable\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(503, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        var failure = assertThrows(IllegalStateException.class,
                () -> gateway("test-secret").suggest("Bache", "incident-triage.v1"));
        assertEquals("Gemini devolvio HTTP 503", failure.getMessage());
        assertFalse(failure.getMessage().contains("test-secret"));
    }

    @Test
    void rejectsIncompleteEmptyAndMalformedResponses() throws Exception {
        assertProviderFailure("{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\"}]}",
                "Gemini no completo la respuesta estructurada");
        assertProviderFailure("{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"\"}]}}]}",
                "Gemini devolvio una respuesta vacia");
        assertProviderFailure("not-json", "Gemini devolvio JSON invalido");
    }

    @Test
    void rejectsMalformedStructuredSuggestion() throws Exception {
        assertProviderFailure("{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"{bad}\"}]}}]}",
                "Gemini devolvio JSON invalido");
    }

    private void assertProviderFailure(String responseBody, String expectedMessage) throws Exception {
        if (server != null) server.stop(0);
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/models/gemini-test:generateContent", exchange -> {
            byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        var failure = assertThrows(IllegalStateException.class,
                () -> gateway("test-secret").suggest("Bache", "incident-triage.v1"));
        assertEquals(expectedMessage, failure.getMessage());
    }

    private GeminiIncidentTriageGateway gateway(String key) {
        return new GeminiIncidentTriageGateway(new ObjectMapper(), new VersionedPromptRepository(), key,
                "gemini-test", "http://localhost:" + (server == null ? 1 : server.getAddress().getPort()),
                java.net.http.HttpClient.newHttpClient());
    }
}
