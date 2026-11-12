package com.example.demo.controller;

import com.example.demo.dto.request.IncidentTriageRequest;
import com.example.demo.dto.response.IncidentTriageSuggestion;
import com.example.demo.service.IncidentTriageService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public-works/assistant/triage")
public class IncidentTriageController {
    private final IncidentTriageService service;
    private final String provider;
    private final String model;

    public IncidentTriageController(IncidentTriageService service,
                                    @Value("${app.ai.provider:mock}") String provider,
                                    @Value("${app.ai.gemini.model:}") String model) {
        this.service = service;
        this.provider = provider;
        this.model = model;
    }

    @PostMapping
    public ResponseEntity<IncidentTriageSuggestion> classify(@Valid @RequestBody IncidentTriageRequest request) {
        var response = ResponseEntity.ok()
                .header("X-AI-Provider", provider.equalsIgnoreCase("gemini") ? "gemini" : "mock-v1")
                .header("X-AI-Prompt-Version", "incident-triage.v1");
        if (provider.equalsIgnoreCase("gemini") && !model.isBlank()) {
            response.header("X-AI-Model", model);
        }
        return response.body(service.classify(request.description()));
    }
}
