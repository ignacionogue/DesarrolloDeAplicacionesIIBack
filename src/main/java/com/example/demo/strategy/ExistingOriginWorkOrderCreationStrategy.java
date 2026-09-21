package com.example.demo.strategy;

import com.example.demo.dto.request.OrdenTrabajoRequest;
import com.example.demo.exception.InvalidRequestException;
import com.example.demo.mapper.OrdenTrabajoMapper;
import com.example.demo.model.*;
import org.springframework.stereotype.Component;

/** Compatibilidad del alta HTTP existente. No consume eventos ni define contratos M6/M7. */
@Component
public class ExistingOriginWorkOrderCreationStrategy implements WorkOrderCreationStrategy {
    private final OrdenTrabajoMapper mapper;

    public ExistingOriginWorkOrderCreationStrategy(OrdenTrabajoMapper mapper) { this.mapper = mapper; }

    @Override public boolean supports(OrigenOT origin) {
        return origin == OrigenOT.ATENCION_CIUDADANA || origin == OrigenOT.INSPECCION;
    }

    @Override public OrdenTrabajo create(OrdenTrabajoRequest request, Cuadrilla crew) {
        if (request.getProjectId() != null) {
            throw new InvalidRequestException("projectId solo se admite para origin PROYECTO");
        }
        return mapper.toEntity(request, crew);
    }
}
