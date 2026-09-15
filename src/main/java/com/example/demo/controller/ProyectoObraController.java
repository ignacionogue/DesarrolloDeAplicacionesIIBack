package com.example.demo.controller;

import com.example.demo.dto.request.ProyectoObraRequest;
import com.example.demo.dto.response.PageResponse;
import com.example.demo.dto.response.ProyectoObraResponse;
import com.example.demo.service.ProyectoObraService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public-works/projects")
@Tag(name = "Proyectos de Obra")
public class ProyectoObraController {

    private final ProyectoObraService service;

    public ProyectoObraController(ProyectoObraService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Listar proyectos con busqueda, filtro por status y paginacion")
    public ResponseEntity<PageResponse<ProyectoObraResponse>> listar(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            Pageable pageable) {
        return ResponseEntity.ok(service.listar(search, status, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProyectoObraResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(service.obtenerPorId(id));
    }

    @PostMapping
    public ResponseEntity<ProyectoObraResponse> crear(@Valid @RequestBody ProyectoObraRequest request) {
        return ResponseEntity.status(201).body(service.crear(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProyectoObraResponse> actualizar(@PathVariable Long id,
                                                            @Valid @RequestBody ProyectoObraRequest request) {
        return ResponseEntity.ok(service.actualizar(id, request));
    }

    @PatchMapping("/{id}/submit-approval")
    @Operation(summary = "BORRADOR -> PENDIENTE_APROBACION")
    public ResponseEntity<ProyectoObraResponse> enviarAAprobacion(@PathVariable Long id) {
        return ResponseEntity.ok(service.enviarAAprobacion(id));
    }

    @PatchMapping("/{id}/approve")
    @Operation(summary = "PENDIENTE_APROBACION -> APROBADO (dispara publicWorksProjectApproved)")
    public ResponseEntity<ProyectoObraResponse> aprobar(@PathVariable Long id) {
        return ResponseEntity.ok(service.aprobar(id));
    }

    @PatchMapping("/{id}/reject")
    @Operation(summary = "PENDIENTE_APROBACION -> RECHAZADO")
    public ResponseEntity<ProyectoObraResponse> rechazar(@PathVariable Long id) {
        return ResponseEntity.ok(service.rechazar(id));
    }
}
