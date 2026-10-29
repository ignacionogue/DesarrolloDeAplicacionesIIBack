package com.example.demo.controller;

import com.example.demo.application.events.*;
import com.example.demo.dto.request.IntegratedClosureRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.Operation;
import java.util.UUID;

@RestController
@RequestMapping("/api/public-works/street-closures/integration")
public class ClosureIntegrationController {
    private final ClosureIntegrationService service;
    public ClosureIntegrationController(ClosureIntegrationService service) {this.service=service;}
    @PostMapping
    @Operation(summary="Solicitar corte o prorroga con tramos estructurados y fechas con offset; requiere contrato M7 confirmado")
    public ResponseEntity<ClosureIntegrationStore.Closure> request(@Valid @RequestBody IntegratedClosureRequest request) {
        return ResponseEntity.status(201).body(service.request(request));
    }
    @GetMapping("/{requestId}")
    public ClosureIntegrationStore.Closure get(@PathVariable UUID requestId) {return service.get(requestId);}
}
