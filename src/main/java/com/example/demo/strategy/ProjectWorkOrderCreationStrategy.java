package com.example.demo.strategy;

import com.example.demo.dto.request.OrdenTrabajoRequest;
import com.example.demo.exception.InvalidRequestException;
import com.example.demo.exception.NotFoundException;
import com.example.demo.mapper.OrdenTrabajoMapper;
import com.example.demo.model.*;
import com.example.demo.repository.ProyectoObraRepository;
import org.springframework.stereotype.Component;

@Component
public class ProjectWorkOrderCreationStrategy implements WorkOrderCreationStrategy {
    private final OrdenTrabajoMapper mapper;
    private final ProyectoObraRepository projects;

    public ProjectWorkOrderCreationStrategy(OrdenTrabajoMapper mapper, ProyectoObraRepository projects) {
        this.mapper = mapper;
        this.projects = projects;
    }

    @Override public boolean supports(OrigenOT origin) { return origin == OrigenOT.PROYECTO; }

    @Override public OrdenTrabajo create(OrdenTrabajoRequest request, Cuadrilla crew) {
        if (request.getProjectId() == null || request.getProjectId() <= 0) {
            throw new InvalidRequestException("projectId debe ser mayor a 0 para origin PROYECTO");
        }
        var project = projects.findById(request.getProjectId())
                .orElseThrow(() -> new NotFoundException("No se encontro el proyecto con id " + request.getProjectId()));
        var order = mapper.toEntity(request, crew);
        order.setProject(project);
        return order;
    }
}
