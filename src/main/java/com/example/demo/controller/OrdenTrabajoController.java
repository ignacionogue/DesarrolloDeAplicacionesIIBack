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
        return ResponseEntity.ok(service.obtenerPorId(id));
    }

    @PostMapping
    public ResponseEntity<OrdenTrabajoResponse> crear(@Valid @RequestBody OrdenTrabajoRequest request) {
        return ResponseEntity.status(201).body(service.crear(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<OrdenTrabajoResponse> actualizar(@PathVariable Long id,
                                                            @Valid @RequestBody OrdenTrabajoRequest request) {
        return ResponseEntity.ok(service.actualizar(id, request));
    }

    @PatchMapping("/{id}/schedule")
    @Operation(summary = "Programar/reprogramar PENDIENTE, PROGRAMADA o ASIGNADA; crew omitido conserva la cuadrilla")
    public ResponseEntity<OrdenTrabajoResponse> programar(@PathVariable Long id,
                                                           @Valid @RequestBody ScheduleOTRequest request) {
        return ResponseEntity.ok(service.programar(id, request));
    }

    @PatchMapping("/{id}/start")
    @Operation(summary = "PROGRAMADA/ASIGNADA/PAUSADA/REABIERTA -> EN_EJECUCION")
    public ResponseEntity<OrdenTrabajoResponse> iniciar(@PathVariable Long id) {
        return ResponseEntity.ok(service.iniciar(id));
    }

    @PatchMapping("/{id}/pause")
    @Operation(summary = "EN_EJECUCION -> PAUSADA")
    public ResponseEntity<OrdenTrabajoResponse> pausar(@PathVariable Long id) {
        return ResponseEntity.ok(service.pausar(id));
    }

    @PatchMapping("/{id}/complete")
    @Operation(summary = "EN_EJECUCION/PAUSADA -> COMPLETADA")
    public ResponseEntity<OrdenTrabajoResponse> completar(@PathVariable Long id,
                                                           @RequestBody(required = false) CompleteOTRequest request) {
        return ResponseEntity.ok(service.completar(id, request != null ? request : new CompleteOTRequest()));
    }

    @PatchMapping("/{id}/validate")
    @Operation(summary = "COMPLETADA -> VALIDADA (approved=true) o REABIERTA (approved=false)")
    public ResponseEntity<OrdenTrabajoResponse> validar(@PathVariable Long id,
                                                         @Valid @RequestBody ValidateOTRequest request) {
        return ResponseEntity.ok(service.validar(id, request));
    }
}
