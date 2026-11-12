package com.example.demo.controller;

import com.example.demo.dto.request.IncidentTriageRequest;
import com.example.demo.dto.response.IncidentTriageSuggestion;
import com.example.demo.service.IncidentTriageService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public-works/assistant/triage")
public class IncidentTriageController {
    private final IncidentTriageService service;

    public IncidentTriageController(IncidentTriageService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<IncidentTriageSuggestion> classify(@Valid @RequestBody IncidentTriageRequest request) {
        return ResponseEntity.ok()
                .header("X-AI-Provider", "mock-v1")
                .header("X-AI-Prompt-Version", "incident-triage.v1")
                .body(service.classify(request.description()));
    }
}
