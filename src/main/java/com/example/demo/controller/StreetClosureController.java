package com.example.demo.controller;

import com.example.demo.dto.request.StreetClosureRequest;
import com.example.demo.dto.response.*;
import com.example.demo.service.StreetClosureService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/public-works/street-closures")
@Tag(name = "Cortes de calle")
public class StreetClosureController {
    private final StreetClosureService service;
    public StreetClosureController(StreetClosureService service) { this.service = service; }
    @GetMapping
    public PageResponse<StreetClosureResponse> list(Pageable pageable) { return service.list(pageable); }
    @PostMapping
    @Operation(summary = "Registrar solicitud PENDIENTE; no envia ni autoriza un corte en Transito")
    public ResponseEntity<StreetClosureResponse> create(@Valid @RequestBody StreetClosureRequest request) {
        return ResponseEntity.status(201).body(service.create(request));
    }
}
