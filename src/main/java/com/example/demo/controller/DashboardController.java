package com.example.demo.controller;

import com.example.demo.dto.response.DashboardSummaryResponse;
import com.example.demo.service.DashboardService;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/public-works/dashboard")
@Tag(name = "Dashboard")
public class DashboardController {
    private final DashboardService service;
    public DashboardController(DashboardService service) { this.service = service; }
    @GetMapping("/summary")
    public DashboardSummaryResponse summary() { return service.summary(); }
}
