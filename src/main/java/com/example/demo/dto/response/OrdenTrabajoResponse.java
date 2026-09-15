package com.example.demo.dto.response;

import java.time.LocalDate;

public record OrdenTrabajoResponse(
        Long id,
        String sourceRequestId,
        String origin,
        String description,
        String interventionType,
        String location,
        String priority,
        String status,
        String crew,
        LocalDate scheduledDate,
        Integer estimatedDurationHours,
        boolean hasEvidence,
        String outcome
) {
}
