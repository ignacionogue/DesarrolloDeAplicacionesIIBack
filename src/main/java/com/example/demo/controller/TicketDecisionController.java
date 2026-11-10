package com.example.demo.controller;

import com.example.demo.application.events.TicketDecisionService;
import com.example.demo.dto.request.TicketUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.Operation;
import java.util.UUID;

@RestController
@RequestMapping("/api/public-works/tickets")
public class TicketDecisionController {
    private final TicketDecisionService service;
    public TicketDecisionController(TicketDecisionService service) {this.service=service;}
    @PostMapping("/{ticketId}/updates")
    @Operation(summary="Encolar una decision para M2; no modifica el estado autoritativo del ticket")
    public ResponseEntity<Void> update(@PathVariable UUID ticketId,@Valid @RequestBody TicketUpdateRequest request) {
        service.update(ticketId,request);return ResponseEntity.accepted().build();
    }
}
