package com.example.demo.mapper;

import com.example.demo.dto.request.OrdenTrabajoRequest;
import com.example.demo.dto.response.OrdenTrabajoResponse;
import com.example.demo.exception.InvalidRequestException;
import com.example.demo.model.Cuadrilla;
import com.example.demo.model.OrdenTrabajo;
import com.example.demo.model.OrigenOT;
import com.example.demo.model.PrioridadOT;
import org.springframework.stereotype.Component;

@Component
public class OrdenTrabajoMapper {

    public OrdenTrabajo toEntity(OrdenTrabajoRequest request, Cuadrilla cuadrilla) {
        return new OrdenTrabajo(
                request.getSourceRequestId(),
                parseOrigin(request.getOrigin()),
                request.getDescription(),
                request.getInterventionType(),
                request.getLocation(),
                parsePriority(request.getPriority()),
                request.getEstimatedDurationHours(),
                cuadrilla
        );
    }

    public void updateEntity(OrdenTrabajo ot, OrdenTrabajoRequest request, Cuadrilla cuadrilla) {
        ot.updateDatosGenerales(
                request.getSourceRequestId(),
                parseOrigin(request.getOrigin()),
                request.getDescription(),
                request.getInterventionType(),
                request.getLocation(),
                parsePriority(request.getPriority()),
                request.getEstimatedDurationHours(),
                cuadrilla
        );
    }

    public OrdenTrabajoResponse toResponse(OrdenTrabajo ot, boolean hasEvidence) {
        return new OrdenTrabajoResponse(
                ot.getId(),
                ot.getSourceRequestId(),
                ot.getOrigin().name(),
                ot.getDescription(),
                ot.getInterventionType(),
                ot.getLocation(),
                ot.getPriority().name(),
                ot.getStatus().name(),
                ot.getCuadrilla() != null ? ot.getCuadrilla().getNombre() : null,
                ot.getScheduledDate(),
                ot.getEstimatedDurationHours(),
                hasEvidence,
                ot.getOutcome()
        );
    }

    private OrigenOT parseOrigin(String value) {
        try {
            return OrigenOT.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException("origin invalido: " + value);
        }
    }

    private PrioridadOT parsePriority(String value) {
        try {
            return PrioridadOT.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException("priority invalido: " + value);
        }
    }
}
