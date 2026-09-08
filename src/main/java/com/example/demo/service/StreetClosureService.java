package com.example.demo.service;

import com.example.demo.dto.request.StreetClosureRequest;
import com.example.demo.dto.response.*;
import com.example.demo.exception.BusinessRuleException;
import com.example.demo.exception.NotFoundException;
import com.example.demo.model.StreetClosure;
import com.example.demo.repository.*;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StreetClosureService {
    private final StreetClosureRepository repository;
    private final OrdenTrabajoRepository orders;
    public StreetClosureService(StreetClosureRepository repository, OrdenTrabajoRepository orders) {
        this.repository = repository; this.orders = orders;
    }
    @Transactional(readOnly = true)
    public PageResponse<StreetClosureResponse> list(Pageable pageable) {
        var sortable = java.util.Set.of("id", "closureRequestId", "location", "status", "requestedFrom", "requestedTo", "reason");
        for (var order : pageable.getSort()) {
            if (!sortable.contains(order.getProperty())) {
                throw new com.example.demo.exception.InvalidRequestException("Campo de ordenamiento invalido: " + order.getProperty());
            }
        }
        return PageResponse.from(repository.findAll(pageable).map(this::response));
    }
    @Transactional
    public StreetClosureResponse create(StreetClosureRequest request) {
        if (request.requestedTo().isBefore(request.requestedFrom())) {
            throw new BusinessRuleException("La fecha final no puede ser anterior a la inicial");
        }
        var order = orders.findById(request.workOrderId()).orElseThrow(() ->
                new NotFoundException("No se encontro la orden de trabajo con id " + request.workOrderId()));
        return response(repository.save(new StreetClosure(order, request.location(), request.affectedSections(),
                request.requestedFrom(), request.requestedTo(), request.reason())));
    }
    private StreetClosureResponse response(StreetClosure c) {
        return new StreetClosureResponse(c.getId(), c.getClosureRequestId(), "public-works",
                c.getWorkOrder().getId(), c.getLocation(), java.util.List.copyOf(c.getAffectedSections()),
                c.getStatus(), c.getRequestedFrom(), c.getRequestedTo(), c.getReason());
    }
}
