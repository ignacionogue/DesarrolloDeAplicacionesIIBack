package com.example.demo.service;

import com.example.demo.application.ai.IncidentTextSanitizer;
import com.example.demo.application.ai.IncidentTriageMetrics;
import com.example.demo.application.ai.IncidentTriageSchemaValidator;
import com.example.demo.application.ai.ResilientIncidentTriageGateway;
import com.example.demo.application.ai.VersionedPromptRepository;
import com.example.demo.dto.response.IncidentTriageSuggestion;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class IncidentTriageService {
    private final boolean enabled;
    private final IncidentTextSanitizer sanitizer;
    private final VersionedPromptRepository prompts;
    private final ResilientIncidentTriageGateway gateway;
    private final IncidentTriageMetrics metrics;
    private final IncidentTriageSchemaValidator validator;

    public IncidentTriageService(@Value("${app.ai.triage-enabled:false}") boolean enabled,
                                 IncidentTextSanitizer sanitizer,
                                 VersionedPromptRepository prompts,
                                 ResilientIncidentTriageGateway gateway,
                                 IncidentTriageMetrics metrics,
                                 IncidentTriageSchemaValidator validator) {
        this.enabled = enabled;
        this.sanitizer = sanitizer;
        this.prompts = prompts;
        this.gateway = gateway;
        this.metrics = metrics;
        this.validator = validator;
    }

    public IncidentTriageSuggestion classify(String description) {
        if (!enabled) {
            metrics.record("disabled", Duration.ZERO);
            return IncidentTriageSuggestion.manual("La asistencia de IA esta deshabilitada");
        }
        var sanitized = sanitizer.sanitize(description);
        if (sanitized.text().isBlank()) {
            metrics.record("empty_after_sanitization", Duration.ZERO);
            return IncidentTriageSuggestion.manual("No quedo informacion util despues de proteger los datos");
        }
        // Loading the prompt here makes the versioned contract part of the runtime path.
        if (prompts.prompt().isBlank()) {
            metrics.record("missing_prompt", Duration.ZERO);
            return IncidentTriageSuggestion.manual("No se pudo cargar el prompt versionado");
        }
        return validator.validateOrFallback(gateway.suggest(sanitized.text(), prompts.version()));
    }
}
