package com.example.demo.application.events;

import com.example.demo.dto.request.IntegratedClosureRequest;
import com.example.demo.exception.*;
import com.example.demo.model.StreetClosure;
import com.example.demo.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.UUID;

@Service
public class ClosureIntegrationService {
    private final OrdenTrabajoRepository orders;
    private final StreetClosureRepository legacy;
    private final ClosureIntegrationStore closures;
    private final EventOutbox outbox;
    private final OutgoingEventValidator contracts;
    private final ObjectMapper json;
    public ClosureIntegrationService(OrdenTrabajoRepository orders,StreetClosureRepository legacy,
            ClosureIntegrationStore closures,EventOutbox outbox,OutgoingEventValidator contracts,ObjectMapper json) {
        this.orders=orders;this.legacy=legacy;this.closures=closures;this.outbox=outbox;this.contracts=contracts;this.json=json;
    }
    @Transactional
    public ClosureIntegrationStore.Closure request(IntegratedClosureRequest request) {
        if(!request.requestedTo().isAfter(request.requestedFrom())) throw new InvalidRequestException("requestedTo debe ser posterior a requestedFrom");
        var order=orders.findLockedById(request.workOrderId()).orElseThrow(()->new NotFoundException("Orden inexistente"));
        UUID correlation=UUID.randomUUID();
        if(request.previousClosureRequestId()!=null) {
            var previous=closures.lock(request.previousClosureRequestId()).orElseThrow(()->new NotFoundException("Solicitud anterior inexistente"));
            if(!previous.orderId().equals(order.getId())) throw new InvalidRequestException("La prorroga debe corresponder a la misma orden");
            if(previous.state().equals("ENDED") || previous.state().equals("REJECTED")) throw new InvalidStateTransitionException("No se puede prorrogar una solicitud finalizada o rechazada");
            correlation=previous.correlationId();
        }
        var closure=legacy.saveAndFlush(new StreetClosure(order,request.affectedSections().get(0).street(),
                request.affectedSections().stream().map(IntegratedClosureRequest.Section::street).toList(),
                request.requestedFrom().toLocalDate(),request.requestedTo().toLocalDate(),request.reason()));
        var data=json.createObjectNode(); data.put("closureRequestId",closure.getClosureRequestId()).put("sourceModule","obras")
                .put("sourceRef",order.getIntegrationId().toString()).put("reason",request.reason())
                .put("requestedFrom",request.requestedFrom().toString()).put("requestedTo",request.requestedTo().toString())
                .put("closureType",request.closureType())
                .put("requestedAt",OffsetDateTime.now(ZoneOffset.UTC).toString());
        data.set("affectedSections",json.valueToTree(request.affectedSections()));
        if(request.previousClosureRequestId()!=null) data.put("previousClosureRequestId",request.previousClosureRequestId().toString());
        var event=EventEnvelope.outgoing("streetClosureRequested",data,correlation,null);
        contracts.validate(event);
        closures.initialize(closure.getId(),event); outbox.append(event,"closure:"+correlation);
        return closures.lock(UUID.fromString(closure.getClosureRequestId())).orElseThrow();
    }
    @Transactional
    public ClosureIntegrationStore.Closure get(UUID requestId) {
        var c=closures.lock(requestId).orElseThrow(()->new NotFoundException("Solicitud integrada inexistente"));
        return new ClosureIntegrationStore.Closure(c.id(),c.orderId(),c.requestId(),c.correlationId(),c.requestEventId(),
                c.effectiveState(OffsetDateTime.now(ZoneOffset.UTC)),c.request(),c.authorization(),c.completion(),c.authorizedStart(),c.authorizedEnd(),c.lastEventAt());
    }
    @Scheduled(fixedDelay=30000)
    @Transactional
    public void refreshWindows() {closures.refreshWindows(OffsetDateTime.now(ZoneOffset.UTC));}
}
