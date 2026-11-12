package com.example.demo.application.ai;

import com.example.demo.dto.response.IncidentTriageSuggestion;

public interface IncidentTriageGateway {
    IncidentTriageSuggestion suggest(String sanitizedDescription, String promptVersion);
}
