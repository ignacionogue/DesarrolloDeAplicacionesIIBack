package com.example.demo.service;

import com.example.demo.dto.request.ProyectoObraRequest;
import com.example.demo.dto.request.ProjectApprovalRequest;
import com.example.demo.dto.response.PageResponse;
import com.example.demo.dto.response.ProyectoObraResponse;
import com.example.demo.exception.InvalidStateTransitionException;
import com.example.demo.exception.NotFoundException;
import com.example.demo.exception.BusinessRuleException;
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
    private final com.example.demo.repository.OrdenTrabajoRepository workOrders;

    public ProyectoObraService(ProyectoObraRepository repository, ProyectoObraMapper mapper,
                                ProjectEventPublisher eventPublisher,
                                com.example.demo.repository.OrdenTrabajoRepository workOrders) {
        this.repository = repository;
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
        this.workOrders = workOrders;
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
        return actualizar(id, request, null);
    }

    @Transactional
    public ProyectoObraResponse actualizar(Long id, ProyectoObraRequest request, Long expectedVersion) {
        ProyectoObra proyecto = buscarOrFallar(id);
        proyecto.requireVersion(expectedVersion);
        if (proyecto.getApprovalStatus() == EstadoAprobacion.APROBADO
                && ((request.getApprovedBudget() != null && (proyecto.getApprovedBudget() == null
                    || request.getApprovedBudget().compareTo(proyecto.getApprovedBudget()) != 0))
                || (request.getApprovedDeadlineDays() != null
                    && !request.getApprovedDeadlineDays().equals(proyecto.getApprovedDeadlineDays())))) {
            throw new BusinessRuleException(
                    "Los datos aprobados no pueden modificarse mediante la edicion general");
        }
        mapper.updateEntity(proyecto, request);
        return mapper.toResponse(repository.save(proyecto));
    }

    @Transactional
    public ProyectoObraResponse enviarAAprobacion(Long id) {
        return enviarAAprobacion(id, null);
    }

    @Transactional
    public ProyectoObraResponse enviarAAprobacion(Long id, Long expectedVersion) {
        ProyectoObra proyecto = buscarOrFallar(id);
        proyecto.requireVersion(expectedVersion);
        validarTransicion(proyecto, EstadoAprobacion.BORRADOR, "enviar a aprobacion");
        proyecto.marcarPendienteAprobacion();
        return mapper.toResponse(repository.save(proyecto));
    }

    @Transactional
    public ProyectoObraResponse aprobar(Long id, ProjectApprovalRequest request) {
        return aprobar(id, request, null);
    }

    @Transactional
    public ProyectoObraResponse aprobar(Long id, ProjectApprovalRequest request, Long expectedVersion) {
        ProyectoObra proyecto = buscarOrFallar(id);
        proyecto.requireVersion(expectedVersion);
        validarTransicion(proyecto, EstadoAprobacion.PENDIENTE_APROBACION, "aprobar");
        proyecto.aprobar(request.approvedBudget(), request.approvedDeadlineDays(), request.approvedAt(), request.observations());
        ProyectoObra guardado = repository.save(proyecto);
        eventPublisher.publishProjectApproved(guardado);
        return mapper.toResponse(guardado);
    }

    @Transactional
    public ProyectoObraResponse finalizar(Long id) {
        return finalizar(id, null);
    }

    @Transactional
    public ProyectoObraResponse finalizar(Long id, Long expectedVersion) {
        ProyectoObra proyecto = repository.findLockedById(id)
                .orElseThrow(() -> new NotFoundException("No se encontro el proyecto con id " + id));
        proyecto.requireVersion(expectedVersion);
        if (proyecto.getApprovalStatus() != EstadoAprobacion.APROBADO
                || proyecto.getActivityStatus() == com.example.demo.model.EstadoActividad.FINALIZADA
                || proyecto.getPhysicalProgress() == null || proyecto.getPhysicalProgress() != 100) {
            throw new BusinessRuleException("La obra debe estar aprobada y tener 100% de avance fisico");
        }
        if (!workOrders.existsByProjectId(id)
                || workOrders.existsByProjectIdAndStatusNot(id, com.example.demo.model.EstadoOT.VALIDADA)) {
            throw new BusinessRuleException("Todas las ordenes de la obra deben estar validadas");
        }
        proyecto.finalizar();
        ProyectoObra guardado = repository.save(proyecto);
        eventPublisher.publishProjectCompleted(guardado);
        return mapper.toResponse(guardado);
    }

    @Transactional
    public ProyectoObraResponse rechazar(Long id) {
        return rechazar(id, null);
    }

    @Transactional
    public ProyectoObraResponse rechazar(Long id, Long expectedVersion) {
        ProyectoObra proyecto = buscarOrFallar(id);
        proyecto.requireVersion(expectedVersion);
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
