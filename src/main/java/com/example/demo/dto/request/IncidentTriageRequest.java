package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IncidentTriageRequest(
        @NotBlank @Size(max = 500) String description
) { }
