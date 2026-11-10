package com.example.demo.infrastructure.ai;

import com.example.demo.application.ai.AiGateway;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** Calls the team-owned AI gateway, never a provider SDK directly. */
@Component
public class HttpAiGateway implements AiGateway {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private final URI gatewayUrl;
    private final String apiKey;
    private final ObjectMapper json;
    private final HttpClient client;

    public HttpAiGateway(@Value("${app.ai.gateway-url:}") String gatewayUrl,
                         @Value("${app.ai.api-key:}") String apiKey,
                         ObjectMapper json) {
        this.gatewayUrl = gatewayUrl == null || gatewayUrl.isBlank() ? null : URI.create(gatewayUrl);
        this.apiKey = apiKey;
        this.json = json;
        this.client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    @Override
    public Result ask(String prompt) {
        if (gatewayUrl == null) return fallback();
        try {
            String body = json.writeValueAsString(Map.of("prompt", prompt, "domain", "public-works"));
            HttpRequest.Builder builder = HttpRequest.newBuilder(gatewayUrl)
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            if (apiKey != null && !apiKey.isBlank()) builder.header("Authorization", "Bearer " + apiKey);
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) return fallback();
            JsonNode payload = json.readTree(response.body());
            String answer = payload.path("answer").asText("").trim();
            return answer.isEmpty() ? fallback() : new Result(answer, false);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return fallback();
        } catch (Exception unavailable) {
            return fallback();
        }
    }

    private static Result fallback() {
        return new Result("El asistente no esta disponible. Consulte el detalle de la orden y vuelva a intentar.", true);
    }
}
