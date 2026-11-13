package com.example.demo.controller;

import com.example.demo.dto.response.IntegrationEventResponse;
import com.example.demo.dto.response.PageResponse;
import com.example.demo.service.IntegrationEventQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public-works/integration-events")
public class IntegrationEventController {
    private final IntegrationEventQueryService service;

    public IntegrationEventController(IntegrationEventQueryService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<IntegrationEventResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "ALL") String direction) {
        return service.list(page, size, direction);
    }
}
