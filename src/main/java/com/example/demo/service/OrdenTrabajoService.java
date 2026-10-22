package com.example.demo.service;

import com.example.demo.dto.request.CompleteOTRequest;
import com.example.demo.dto.request.OrdenTrabajoRequest;
import com.example.demo.dto.request.ScheduleOTRequest;
import com.example.demo.dto.request.ValidateOTRequest;
import com.example.demo.dto.response.OrdenTrabajoResponse;
import com.example.demo.dto.response.PageResponse;
import com.example.demo.exception.InvalidStateTransitionException;
import com.example.demo.exception.NotFoundException;
import com.example.demo.mapper.OrdenTrabajoMapper;
import com.example.demo.strategy.WorkOrderCreationStrategyResolver;
import com.example.demo.model.Cuadrilla;
import com.example.demo.model.EstadoOT;
import com.example.demo.model.Observacion;
import com.example.demo.model.OrdenTrabajo;
import com.example.demo.repository.CuadrillaRepository;
import com.example.demo.repository.EvidenciaRepository;
import com.example.demo.repository.ObservacionRepository;
import com.example.demo.repository.OrdenTrabajoRepository;
import com.example.demo.repository.OrdenTrabajoSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import com.example.demo.application.events.WorkOrderEventHandler;
import com.example.demo.application.events.WorkOrderEventHandler.Action;

@Service
public class OrdenTrabajoService {

    private final OrdenTrabajoRepository repository;
    private final OrdenTrabajoMapper mapper;
    private final CuadrillaRepository cuadrillaRepository;
    private final EvidenciaRepository evidenciaRepository;
    private final ObservacionRepository observacionRepository;
    private final WorkOrderCreationStrategyResolver creationStrategies;
    private final List<WorkOrderEventHandler> eventHandlers;

    public OrdenTrabajoService(OrdenTrabajoRepository repository, OrdenTrabajoMapper mapper,
                                CuadrillaRepository cuadrillaRepository, EvidenciaRepository evidenciaRepository,
                                ObservacionRepository observacionRepository,
                                WorkOrderCreationStrategyResolver creationStrategies, List<WorkOrderEventHandler> eventHandlers) {
        this.repository = repository;
        this.mapper = mapper;
        this.cuadrillaRepository = cuadrillaRepository;
        this.evidenciaRepository = evidenciaRepository;
        this.observacionRepository = observacionRepository;
        this.creationStrategies = creationStrategies;
        this.eventHandlers = eventHandlers;
    }

    @Transactional(readOnly = true)
    public PageResponse<OrdenTrabajoResponse> listar(String search, String status, String priority,
                                                      String origin, Pageable pageable) {
        Page<OrdenTrabajo> page = repository.findAll(
                OrdenTrabajoSpecifications.withFilters(search, status, priority, origin), pageable);

        List<Long> ids = page.getContent().stream().map(OrdenTrabajo::getId).toList();
        Set<Long> conEvidencia = evidenciaRepository.findByOrdenTrabajoIdIn(ids).stream()
                .map(e -> e.getOrdenTrabajo().getId())
                .collect(Collectors.toSet());

        return PageResponse.from(page.map(ot -> mapper.toResponse(ot, conEvidencia.contains(ot.getId()))));
    }

    @Transactional(readOnly = true)
    public OrdenTrabajoResponse obtenerPorId(Long id) {
        OrdenTrabajo ot = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("No se encontro la orden de trabajo con id " + id));
        return mapper.toResponse(ot, evidenciaRepository.existsByOrdenTrabajoId(id));
    }

    @Transactional
    public OrdenTrabajoResponse crear(OrdenTrabajoRequest request) {
        Cuadrilla cuadrilla = resolverCuadrilla(request.getCrew());
        OrdenTrabajo ot = creationStrategies.resolve(request.getOrigin()).create(request, cuadrilla);
        eventHandlers.forEach(handler -> handler.before(ot, Action.CREATE));
        OrdenTrabajo guardada = repository.save(ot);
        eventHandlers.forEach(handler -> handler.after(guardada, Action.CREATE));
        // Recien creada: no puede tener evidencia todavia.
        return mapper.toResponse(guardada, false);
    }

    @Transactional
    public OrdenTrabajoResponse actualizar(Long id, OrdenTrabajoRequest request) {
        OrdenTrabajo ot = buscarOrFallar(id);
        eventHandlers.forEach(handler -> handler.before(ot, Action.EDIT));
        Cuadrilla cuadrilla = resolverCuadrilla(request.getCrew());
        // Revalidate origin/project on PUT without replacing identity or workflow state.
        var proposed = creationStrategies.resolve(request.getOrigin()).create(request, cuadrilla);
        eventHandlers.forEach(handler -> handler.beforeEdit(ot, proposed));
        mapper.updateEntity(ot, proposed);
        OrdenTrabajo guardada = repository.save(ot);
        return mapper.toResponse(guardada, evidenciaRepository.existsByOrdenTrabajoId(id));
    }

    @Transactional
    public OrdenTrabajoResponse programar(Long id, ScheduleOTRequest request) {
        OrdenTrabajo ot = buscarOrFallar(id);
        validarTransicion(ot, "programar", EstadoOT.PENDIENTE, EstadoOT.PROGRAMADA, EstadoOT.ASIGNADA);
        eventHandlers.forEach(handler -> handler.before(ot, Action.SCHEDULE));
        if (request.getCrew() != null && request.getCrew().isBlank()) {
            throw new com.example.demo.exception.InvalidRequestException("crew no puede estar vacio; omitalo para conservar la cuadrilla");
        }
        Cuadrilla cuadrilla = request.getCrew() == null ? ot.getCuadrilla() : resolverCuadrilla(request.getCrew());
        ot.programar(request.getScheduledDate(), cuadrilla);
        eventHandlers.forEach(handler -> handler.after(ot, Action.SCHEDULE));
        return responderConEvidencia(repository.save(ot));
    }

    @Transactional
    public OrdenTrabajoResponse iniciar(Long id) {
        OrdenTrabajo ot = buscarOrFallar(id);
        validarTransicion(ot, "iniciar", EstadoOT.PROGRAMADA, EstadoOT.ASIGNADA, EstadoOT.PAUSADA, EstadoOT.REABIERTA);
        eventHandlers.forEach(handler -> handler.before(ot, Action.START));
        ot.iniciar();
        eventHandlers.forEach(handler -> handler.after(ot, Action.START));
        return responderConEvidencia(repository.save(ot));
    }

    @Transactional
    public OrdenTrabajoResponse pausar(Long id) {
        OrdenTrabajo ot = buscarOrFallar(id);
        validarTransicion(ot, "pausar", EstadoOT.EN_EJECUCION);
        eventHandlers.forEach(handler -> handler.before(ot, Action.PAUSE));
        ot.pausar();
        eventHandlers.forEach(handler -> handler.after(ot, Action.PAUSE));
        return responderConEvidencia(repository.save(ot));
    }

    @Transactional
    public OrdenTrabajoResponse completar(Long id, CompleteOTRequest request) {
        OrdenTrabajo ot = buscarOrFallar(id);
        validarTransicion(ot, "completar", EstadoOT.EN_EJECUCION, EstadoOT.PAUSADA);
        eventHandlers.forEach(handler -> handler.before(ot, Action.COMPLETE));
        ot.completar(request.getOutcome());
        eventHandlers.forEach(handler -> handler.after(ot, Action.COMPLETE));
        return responderConEvidencia(repository.save(ot));
    }

    @Transactional
    public OrdenTrabajoResponse validar(Long id, ValidateOTRequest request) {
        OrdenTrabajo ot = buscarOrFallar(id);
        validarTransicion(ot, "validar", EstadoOT.COMPLETADA);
        eventHandlers.forEach(handler -> handler.before(ot, Action.VALIDATE));
        if (request.getObservations() != null && !request.getObservations().isBlank()) {
            observacionRepository.save(new Observacion(ot, request.getObservations()));
        }
        ot.validar(Boolean.TRUE.equals(request.getApproved()));
        return responderConEvidencia(repository.save(ot));
    }

    private OrdenTrabajoResponse responderConEvidencia(OrdenTrabajo ot) {
        return mapper.toResponse(ot, evidenciaRepository.existsByOrdenTrabajoId(ot.getId()));
    }

    private Cuadrilla resolverCuadrilla(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return null;
        }
        return cuadrillaRepository.findByNombreIgnoreCase(nombre.trim())
                .orElseThrow(() -> new NotFoundException("No se encontro la cuadrilla: " + nombre));
    }

    private void validarTransicion(OrdenTrabajo ot, String accion, EstadoOT... estadosValidos) {
        Set<EstadoOT> permitidos = EnumSet.copyOf(List.of(estadosValidos));
        if (!permitidos.contains(ot.getStatus())) {
            throw new InvalidStateTransitionException(
                    "No se puede " + accion + " una orden de trabajo en estado " + ot.getStatus());
        }
    }

    private OrdenTrabajo buscarOrFallar(Long id) {
        return repository.findLockedById(id)
                .orElseThrow(() -> new NotFoundException("No se encontro la orden de trabajo con id " + id));
    }
}
