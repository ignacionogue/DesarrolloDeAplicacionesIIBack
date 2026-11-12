package com.example.demo.infrastructure.ai;

import com.example.demo.application.ai.IncidentTriageGateway;
import com.example.demo.application.ai.VersionedPromptRepository;
import com.example.demo.dto.response.IncidentTriageSuggestion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Gemini adapter. It sends only sanitized text and accepts structured JSON only. */
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "gemini")
public class GeminiIncidentTriageGateway implements IncidentTriageGateway {
    private final ObjectMapper json;
    private final VersionedPromptRepository prompts;
    private final HttpClient client;
    private final String apiKey;
    private final String model;
    private final URI endpoint;
    private final JsonNode providerSchema;

    @Autowired
    public GeminiIncidentTriageGateway(ObjectMapper json,
                                       VersionedPromptRepository prompts,
                                       @Value("${app.ai.gemini.api-key:}") String apiKey,
                                       @Value("${app.ai.gemini.model:gemini-3.5-flash}") String model,
                                       @Value("${app.ai.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}") String baseUrl,
                                       @Value("${app.ai.timeout-ms:5000}") long timeoutMs) {
        this(json, prompts, apiKey, model, baseUrl,
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build());
    }

    GeminiIncidentTriageGateway(ObjectMapper json, VersionedPromptRepository prompts,
                                String apiKey, String model, String baseUrl, HttpClient client) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY es obligatorio cuando AI_PROVIDER=gemini");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalStateException("GEMINI_MODEL no puede estar vacio");
        }
        this.json = json;
        this.prompts = prompts;
        this.apiKey = apiKey;
        this.model = model;
        this.client = client;
        this.endpoint = URI.create(stripTrailingSlash(baseUrl) + "/models/" + model + ":generateContent");
        this.providerSchema = loadProviderSchema(json);
    }

    @Override
    public IncidentTriageSuggestion suggest(String sanitizedDescription, String promptVersion) {
        try {
            String requestBody = json.writeValueAsString(buildRequest(sanitizedDescription, promptVersion));
            var request = HttpRequest.newBuilder(endpoint)
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Gemini devolvio HTTP " + response.statusCode());
            }
            JsonNode document = json.readTree(response.body());
            JsonNode candidate = document.path("candidates").path(0);
            if (!"STOP".equals(candidate.path("finishReason").asText())) {
                throw new IllegalStateException("Gemini no completo la respuesta estructurada");
            }
            String text = candidate.path("content").path("parts").path(0).path("text").asText();
            if (text.isBlank()) throw new IllegalStateException("Gemini devolvio una respuesta vacia");
            return json.readValue(text, IncidentTriageSuggestion.class);
        } catch (IOException error) {
            throw new IllegalStateException("Gemini devolvio JSON invalido", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("La llamada a Gemini fue interrumpida", error);
        }
    }

    private ObjectNode buildRequest(String sanitizedDescription, String promptVersion) {
        var root = json.createObjectNode();
        var system = root.putObject("systemInstruction");
        system.putArray("parts").addObject().put("text", prompts.prompt());
        var contents = root.putArray("contents").addObject();
        contents.put("role", "user");
        contents.putArray("parts").addObject().put("text",
                "Version del contrato: " + promptVersion + "\nDescripcion sanitizada:\n" + sanitizedDescription);
        var config = root.putObject("generationConfig");
        config.put("responseMimeType", "application/json");
        config.set("responseJsonSchema", providerSchema);
        config.put("maxOutputTokens", 1500);
        config.putObject("thinkingConfig").put("thinkingLevel", "MINIMAL");
        return root;
    }

    private static JsonNode loadProviderSchema(ObjectMapper json) {
        try (var input = new ClassPathResource("ai/schemas/incident-triage-response.schema.json").getInputStream()) {
            ObjectNode schema = (ObjectNode) json.readTree(input);
            schema.remove("$schema");
            schema.remove("$id");
            ObjectNode review = (ObjectNode) schema.path("properties").path("humanReviewRequired");
            review.remove("const");
            review.put("type", "boolean");
            return schema;
        } catch (IOException error) {
            throw new IllegalStateException("No se pudo cargar el schema para Gemini", error);
        }
    }

    private static String stripTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
