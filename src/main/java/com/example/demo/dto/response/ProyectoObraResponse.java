package com.example.demo.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Forma exacta en la que el Front recibe un ProyectoObra (contrato del punto 4).
 * 'status' es el resultado de combinar approvalStatus/activityStatus (ver mapper).
 * 'budgetProgress' se calcula, no se persiste.
 */
public record ProyectoObraResponse(
        Long id,
        String name,
        String description,
        String scope,
        String location,
        BigDecimal estimatedBudget,
        BigDecimal approvedBudget,
        LocalDate estimatedStartDate,
        Integer estimatedDurationDays,
        Integer approvedDeadlineDays,
        Integer physicalProgress,
        Integer budgetProgress,
        String status,
        String technicalManager,
        String contractor
) {
}
