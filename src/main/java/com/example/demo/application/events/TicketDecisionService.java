package com.example.demo.application.events;

import com.example.demo.dto.request.TicketUpdateRequest;
import com.example.demo.exception.*;
import com.example.demo.repository.OrdenTrabajoRepository;
import com.example.demo.model.EstadoOT;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.util.*;

@Service
public class TicketDecisionService {
    private final TicketProjectionStore tickets;
    private final TicketStatusPublisher publisher;
    private final OrdenTrabajoRepository orders;
    private final ObjectMapper json;
    public TicketDecisionService(TicketProjectionStore tickets,TicketStatusPublisher publisher,OrdenTrabajoRepository orders,ObjectMapper json) {
        this.tickets=tickets;this.publisher=publisher;this.orders=orders;this.json=json;
    }
    @Transactional
    public void update(UUID ticketId,TicketUpdateRequest request) {
        var p=tickets.lock(ticketId).orElseThrow(()->new NotFoundException("Ticket no recibido por Obras"));
        if(Set.of("CANCELLED","CLOSED","RESOLVED","DUPLICATE").contains(p.snapshot().path("currentStatus").asString()))
            throw new InvalidStateTransitionException("El ticket no permite nuevas acciones");
        if(p.routing()==null) throw new InvalidStateTransitionException("Falta la derivacion ROUTED");
        String type=request.updateType().name();
        if(type.equals("RESOLVED") && (p.orderId()==null || orders.findById(p.orderId())
                .filter(o->Set.of(EstadoOT.COMPLETADA,EstadoOT.VALIDADA).contains(o.getStatus())).isEmpty()))
            throw new InvalidStateTransitionException("Solo se puede resolver al finalizar la intervencion");
        var content=json.createObjectNode();
        if(request.publicMessage()!=null) content.put("publicMessage",request.publicMessage());
        if(request.internalMessage()!=null) content.put("internalMessage",request.internalMessage());
        if(request.progress()!=null) content.put("progress",request.progress());
        if(request.details()!=null) content.set("details",request.details());
        if(request.attachments()!=null) content.set("attachments",request.attachments());
        boolean started=p.started();
        try {
            if(type.equals("PROGRESS") && !started && p.snapshot().path("currentStatus").asString().equals("ROUTED")) {
                publisher.publish(ticketId,"STARTED",null,p.correlationId(),null); started=true;
            }
            publisher.publish(ticketId,type,content,p.correlationId(),null);
        } catch(InvalidEventException invalid) {throw new InvalidRequestException("Los datos no cumplen el contrato updateTicketStatus v1.78");}
        if(type.equals("STARTED")) started=true;
        tickets.save(new TicketProjectionStore.Projection(p.ticketId(),p.snapshot(),p.updatedAt(),p.routing(),p.routingAt(),p.information(),
                p.informationAt(),p.escalation(),p.escalationAt(),p.correlationId(),p.orderId(),started,p.planningReviewRequired(),p.evaluationRequired()));
    }
}
