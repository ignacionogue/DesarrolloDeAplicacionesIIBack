package com.example.demo.dto.response;

import java.time.LocalDate;
import java.util.List;

public record StreetClosureResponse(Long id, String closureRequestId, String sourceModule,
        Long workOrderId, String location, List<String> affectedSections, String status,
        LocalDate requestedFrom, LocalDate requestedTo, String reason) {}
