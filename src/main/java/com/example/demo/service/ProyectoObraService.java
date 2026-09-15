package com.example.demo.service;

import com.example.demo.dto.request.ProyectoObraRequest;
import com.example.demo.dto.response.PageResponse;
import com.example.demo.dto.response.ProyectoObraResponse;
import com.example.demo.exception.InvalidStateTransitionException;
import com.example.demo.exception.NotFoundException;
import com.example.demo.integration.ProjectEventPublisher;
import com.example.demo.mapper.ProyectoObraMapper;
import com.example.demo.model.EstadoAprobacion;
import com.example.demo.model.ProyectoObra;
import com.example.demo.repository.ProyectoObraRepository;
import com.example.demo.repository.ProyectoObraSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProyectoObraService {

    private final ProyectoObraRepository repository;
    private final ProyectoObraMapper mapper;
    private final ProjectEventPublisher eventPublisher;

    public ProyectoObraService(ProyectoObraRepository repository, ProyectoObraMapper mapper,
                                ProjectEventPublisher eventPublisher) {
        this.repository = repository;
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProyectoObraResponse> listar(String search, String status, Pageable pageable) {
        Page<ProyectoObra> page = repository.findAll(
                ProyectoObraSpecifications.withFilters(search, status), pageable);
        return PageResponse.from(page.map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    public ProyectoObraResponse obtenerPorId(Long id) {
        return mapper.toResponse(buscarOrFallar(id));
    }

    @Transactional
    public ProyectoObraResponse crear(ProyectoObraRequest request) {
        ProyectoObra proyecto = mapper.toEntity(request);
        return mapper.toResponse(repository.save(proyecto));
    }

    @Transactional
    public ProyectoObraResponse actualizar(Long id, ProyectoObraRequest request) {
        ProyectoObra proyecto = buscarOrFallar(id);
        mapper.updateEntity(proyecto, request);
        return mapper.toResponse(repository.save(proyecto));
    }

    @Transactional
    public ProyectoObraResponse enviarAAprobacion(Long id) {
        ProyectoObra proyecto = buscarOrFallar(id);
        validarTransicion(proyecto, EstadoAprobacion.BORRADOR, "enviar a aprobacion");
        proyecto.marcarPendienteAprobacion();
        return mapper.toResponse(repository.save(proyecto));
    }

    @Transactional
    public ProyectoObraResponse aprobar(Long id) {
        ProyectoObra proyecto = buscarOrFallar(id);
        validarTransicion(proyecto, EstadoAprobacion.PENDIENTE_APROBACION, "aprobar");
        proyecto.aprobar();
        ProyectoObra guardado = repository.save(proyecto);
        eventPublisher.publishProjectApproved(guardado.getId(), guardado.getName());
        return mapper.toResponse(guardado);
    }

    @Transactional
    public ProyectoObraResponse rechazar(Long id) {
        ProyectoObra proyecto = buscarOrFallar(id);
        validarTransicion(proyecto, EstadoAprobacion.PENDIENTE_APROBACION, "rechazar");
        proyecto.rechazar();
        return mapper.toResponse(repository.save(proyecto));
    }

    private void validarTransicion(ProyectoObra proyecto, EstadoAprobacion estadoRequerido, String accion) {
        if (proyecto.getApprovalStatus() != estadoRequerido) {
            throw new InvalidStateTransitionException(
                    "No se puede " + accion + " un proyecto en estado " + proyecto.getApprovalStatus());
        }
    }

    private ProyectoObra buscarOrFallar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("No se encontro el proyecto con id " + id));
    }
}
