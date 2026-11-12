package com.example.demo.application.ai;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class VersionedPromptRepository {
    public static final String VERSION = "incident-triage.v1";
    private final String prompt;

    public VersionedPromptRepository() {
        try (var input = new ClassPathResource("ai/prompts/incident-triage.v1.md").getInputStream()) {
            prompt = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("No se pudo cargar el prompt versionado", error);
        }
    }

    public String version() { return VERSION; }
    public String prompt() { return prompt; }
}
