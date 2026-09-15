package com.example.demo.controller;

import com.example.demo.dto.request.CuadrillaRequest;
import com.example.demo.dto.response.CuadrillaResponse;
import com.example.demo.service.CuadrillaService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/public-works/crews")
@Tag(name = "Cuadrillas")
public class CuadrillaController {

    private final CuadrillaService service;

    public CuadrillaController(CuadrillaService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<CuadrillaResponse>> listar() {
        return ResponseEntity.ok(service.listar());
    }

    @PostMapping
    public ResponseEntity<CuadrillaResponse> crear(@Valid @RequestBody CuadrillaRequest request) {
        return ResponseEntity.status(201).body(service.crear(request));
    }
}
