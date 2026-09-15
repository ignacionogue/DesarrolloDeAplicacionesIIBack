package com.example.demo.controller;

import com.example.demo.dto.response.ResourcesSummaryResponse;
import com.example.demo.service.ResourcesService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public-works/resources")
@Tag(name = "Recursos")
public class ResourcesController {

    private final ResourcesService service;

    public ResourcesController(ResourcesService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ResourcesSummaryResponse> obtenerResumen() {
        return ResponseEntity.ok(service.obtenerResumen());
    }
}
