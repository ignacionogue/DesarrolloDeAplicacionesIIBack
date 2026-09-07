package com.example.demo;

import com.example.demo.dto.request.ProyectoObraRequest;
import com.example.demo.mapper.ProyectoObraMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
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
}
