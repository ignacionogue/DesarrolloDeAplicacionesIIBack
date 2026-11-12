package com.example.demo.controller;

import com.example.demo.dto.request.IncidentTriageRequest;
import com.example.demo.dto.response.IncidentTriageSuggestion;
import com.example.demo.service.IncidentTriageService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IncidentTriageControllerTest {
    @Test
    void exposesConfiguredGeminiMetadataWithoutSecrets() {
        var service = mock(IncidentTriageService.class);
        when(service.classify("Bache")).thenReturn(IncidentTriageSuggestion.manual("review"));
        var controller = new IncidentTriageController(service, "gemini", "gemini-3.5-flash");

        var response = controller.classify(new IncidentTriageRequest("Bache"));

        assertEquals("gemini", response.getHeaders().getFirst("X-AI-Provider"));
        assertEquals("gemini-3.5-flash", response.getHeaders().getFirst("X-AI-Model"));
        assertEquals("incident-triage.v1", response.getHeaders().getFirst("X-AI-Prompt-Version"));
    }
}
