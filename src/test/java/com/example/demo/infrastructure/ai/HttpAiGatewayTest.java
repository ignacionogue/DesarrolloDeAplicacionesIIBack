package com.example.demo.infrastructure.ai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class HttpAiGatewayTest {
    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> authorization = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok", exchange -> respond(exchange, 200, "{\"answer\":\"Respuesta segura\"}"));
        server.createContext("/empty", exchange -> respond(exchange, 200, "{\"answer\":\"   \"}"));
        server.createContext("/bad-json", exchange -> respond(exchange, 200, "not-json"));
        server.createContext("/unavailable", exchange -> respond(exchange, 503, "{}"));
        server.createContext("/authenticated", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "{\"answer\":\"Autenticada\"}");
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void returnsProviderAnswerAndSendsOptionalBearerSecret() {
        var plain = new HttpAiGateway(baseUrl + "/ok", "", new ObjectMapper()).ask("estado de obra");
        assertEquals("Respuesta segura", plain.answer());
        assertFalse(plain.fallback());

        var authenticated = new HttpAiGateway(baseUrl + "/authenticated", "test-secret", new ObjectMapper())
                .ask("estado de obra");
        assertEquals("Autenticada", authenticated.answer());
        assertEquals("Bearer test-secret", authorization.get());
    }

    @Test
    void fallsBackForMissingGatewayInvalidResponsesAndProviderErrors() {
        assertTrue(new HttpAiGateway(" ", null, new ObjectMapper()).ask("obra").fallback());
        assertTrue(new HttpAiGateway(baseUrl + "/empty", null, new ObjectMapper()).ask("obra").fallback());
        assertTrue(new HttpAiGateway(baseUrl + "/bad-json", null, new ObjectMapper()).ask("obra").fallback());
        assertTrue(new HttpAiGateway(baseUrl + "/unavailable", null, new ObjectMapper()).ask("obra").fallback());
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
