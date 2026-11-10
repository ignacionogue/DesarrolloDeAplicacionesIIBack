package com.example.demo.controller;

import com.example.demo.dto.request.CompleteOTRequest;
import com.example.demo.dto.request.OrdenTrabajoRequest;
import com.example.demo.dto.request.ScheduleOTRequest;
import com.example.demo.dto.request.ValidateOTRequest;
import com.example.demo.dto.response.OrdenTrabajoResponse;
import com.example.demo.dto.response.PageResponse;
import com.example.demo.service.OrdenTrabajoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public-works/work-orders")
@Tag(name = "Ordenes de Trabajo")
public class OrdenTrabajoController {

    private final OrdenTrabajoService service;

    public OrdenTrabajoController(OrdenTrabajoService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<PageResponse<OrdenTrabajoResponse>> listar(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String origin,
            Pageable pageable) {
        return ResponseEntity.ok(service.listar(search, status, priority, origin, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrdenTrabajoResponse> obtenerPorId(@PathVariable Long id) {
        return versioned(service.obtenerPorId(id));
    }

    @PostMapping
    public ResponseEntity<OrdenTrabajoResponse> crear(@Valid @RequestBody OrdenTrabajoRequest request) {
        OrdenTrabajoResponse body = service.crear(request);
        return ResponseEntity.status(201).eTag(EtagPreconditions.value(body.version())).body(body);
    }

    @PutMapping("/{id}")
    public ResponseEntity<OrdenTrabajoResponse> actualizar(@PathVariable Long id,
                                                            @Valid @RequestBody OrdenTrabajoRequest request,
                                                            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        return versioned(service.actualizar(id, request, EtagPreconditions.parse(ifMatch)));
    }

    @PatchMapping("/{id}/schedule")
    @Operation(summary = "Programar/reprogramar PENDIENTE, PROGRAMADA o ASIGNADA; crew omitido conserva la cuadrilla")
    public ResponseEntity<OrdenTrabajoResponse> programar(@PathVariable Long id,
                                                           @Valid @RequestBody ScheduleOTRequest request,
                                                           @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        return versioned(service.programar(id, request, EtagPreconditions.parse(ifMatch)));
    }

    @PatchMapping("/{id}/start")
    @Operation(summary = "PROGRAMADA/ASIGNADA/PAUSADA/REABIERTA -> EN_EJECUCION")
    public ResponseEntity<OrdenTrabajoResponse> iniciar(@PathVariable Long id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        return versioned(service.iniciar(id, EtagPreconditions.parse(ifMatch)));
    }

    @PatchMapping("/{id}/pause")
    @Operation(summary = "EN_EJECUCION -> PAUSADA")
    public ResponseEntity<OrdenTrabajoResponse> pausar(@PathVariable Long id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        return versioned(service.pausar(id, EtagPreconditions.parse(ifMatch)));
    }

    @PatchMapping("/{id}/complete")
    @Operation(summary = "EN_EJECUCION/PAUSADA -> COMPLETADA")
    public ResponseEntity<OrdenTrabajoResponse> completar(@PathVariable Long id,
                                                           @Valid @RequestBody(required = false) CompleteOTRequest request,
                                                           @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        return versioned(service.completar(id, request != null ? request : new CompleteOTRequest(),
                EtagPreconditions.parse(ifMatch)));
    }

    @PatchMapping("/{id}/validate")
    @Operation(summary = "COMPLETADA -> VALIDADA (approved=true) o REABIERTA (approved=false)")
    public ResponseEntity<OrdenTrabajoResponse> validar(@PathVariable Long id,
                                                         @Valid @RequestBody ValidateOTRequest request,
                                                         @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        return versioned(service.validar(id, request, EtagPreconditions.parse(ifMatch)));
    }

    private static ResponseEntity<OrdenTrabajoResponse> versioned(OrdenTrabajoResponse body) {
        return ResponseEntity.ok().eTag(EtagPreconditions.value(body.version())).body(body);
    }
}
