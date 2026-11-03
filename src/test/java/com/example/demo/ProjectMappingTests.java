package com.example.demo;

import com.example.demo.dto.request.ProyectoObraRequest;
import com.example.demo.mapper.ProyectoObraMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectMappingTests {
    @Test
    void creationPreservesProgressAndUsedBudget() {
        var request = new ProyectoObraRequest();
        request.setUsedBudget(new BigDecimal("200000"));
        request.setPhysicalProgress(25);
        var entity = new ProyectoObraMapper().toEntity(request);
        assertEquals(new BigDecimal("200000"), entity.getUsedBudget());
        assertEquals(25, entity.getPhysicalProgress());
    }

    @Test
    void omittedProgressKeepsZeroDefaults() {
        var entity = new ProyectoObraMapper().toEntity(new ProyectoObraRequest());
        assertEquals(BigDecimal.ZERO, entity.getUsedBudget());
        assertEquals(0, entity.getPhysicalProgress());
    }

    @Test
    void updateCannotOverwriteApprovedBudgetOrDeadline() {
        var mapper = new ProyectoObraMapper();
        var entity = mapper.toEntity(new ProyectoObraRequest());
        entity.aprobar(new BigDecimal("1500000"), 45, LocalDate.of(2026, 11, 3), "Aprobado");

        var attemptedUpdate = new ProyectoObraRequest();
        attemptedUpdate.setApprovedBudget(new BigDecimal("9999999"));
        attemptedUpdate.setApprovedDeadlineDays(999);
        mapper.updateEntity(entity, attemptedUpdate);

        assertEquals(new BigDecimal("1500000"), entity.getApprovedBudget());
        assertEquals(45, entity.getApprovedDeadlineDays());
    }
}
