package com.example.demo.controller;

import com.example.demo.dto.request.AiAssistRequest;
import com.example.demo.dto.response.AiAssistResponse;
import com.example.demo.service.PublicWorksAssistantService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public-works/assistant")
public class AiAssistantController {
    private final PublicWorksAssistantService service;

    public AiAssistantController(PublicWorksAssistantService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<AiAssistResponse> assist(@Valid @RequestBody AiAssistRequest request) {
        return ResponseEntity.ok(service.assist(request));
    }
}
