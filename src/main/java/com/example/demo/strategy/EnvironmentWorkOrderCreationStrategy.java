package com.example.demo.strategy;

import com.example.demo.application.events.EnvironmentCaseStore;
import com.example.demo.dto.request.OrdenTrabajoRequest;
import com.example.demo.exception.InvalidRequestException;
import com.example.demo.mapper.OrdenTrabajoMapper;
import com.example.demo.model.*;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class EnvironmentWorkOrderCreationStrategy implements WorkOrderCreationStrategy {
    private final EnvironmentCaseStore cases;
    private final OrdenTrabajoMapper mapper;
    public EnvironmentWorkOrderCreationStrategy(EnvironmentCaseStore cases, OrdenTrabajoMapper mapper) { this.cases=cases; this.mapper=mapper; }
    public boolean supports(OrigenOT origin) { return origin==OrigenOT.AMBIENTE; }
    public OrdenTrabajo create(OrdenTrabajoRequest request, Cuadrilla crew) {
        if(request.getProjectId()!=null) throw new InvalidRequestException("AMBIENTE no admite projectId");
        UUID id;
        try { id=UUID.fromString(request.getSourceRequestId()); }
        catch(IllegalArgumentException | NullPointerException ex) { throw new InvalidRequestException("sourceRequestId debe ser UUID"); }
        var source=cases.lock(id).orElseThrow(()->new InvalidRequestException("No existe una solicitud recibida de Ambiente"));
        if(!source.actionable()) throw new InvalidRequestException("El aviso permanece en monitoreo sin intervencion sugerida");
        return mapper.toEntity(request,crew);
    }
}
