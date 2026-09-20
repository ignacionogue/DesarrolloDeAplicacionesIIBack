package com.example.demo.strategy;

import com.example.demo.dto.request.OrdenTrabajoRequest;
import com.example.demo.exception.InvalidRequestException;
import com.example.demo.mapper.OrdenTrabajoMapper;
import com.example.demo.model.*;
import org.springframework.stereotype.Component;

@Component
public class ManualWorkOrderCreationStrategy implements WorkOrderCreationStrategy {
    private final OrdenTrabajoMapper mapper;

    public ManualWorkOrderCreationStrategy(OrdenTrabajoMapper mapper) { this.mapper = mapper; }

    @Override public boolean supports(OrigenOT origin) { return origin == OrigenOT.MANUAL; }

    @Override public OrdenTrabajo create(OrdenTrabajoRequest request, Cuadrilla crew) {
        if (request.getProjectId() != null) {
            throw new InvalidRequestException("Una orden MANUAL no admite projectId; utilice origin PROYECTO");
        }
        return mapper.toEntity(request, crew);
    }
}
