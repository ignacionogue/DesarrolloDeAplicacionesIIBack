package com.example.demo.application.events;

import com.example.demo.model.*;
import com.example.demo.exception.InvalidStateTransitionException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.Optional;
import static com.example.demo.application.events.TicketProjectionStore.Projection;

@Component
public class TicketWorkOrderEvents implements WorkOrderEventHandler {
    private final TicketProjectionStore tickets;
    private final TicketStatusPublisher publisher;
    private final ObjectMapper json;
    public TicketWorkOrderEvents(TicketProjectionStore tickets, TicketStatusPublisher publisher, ObjectMapper json) {
        this.tickets=tickets; this.publisher=publisher; this.json=json;
    }
    private Optional<Projection> projection(OrdenTrabajo order) {
        if(order.getOrigin()!=OrigenOT.ATENCION_CIUDADANA || order.getSourceRequestId()==null) return Optional.empty();
        UUID id;
        try { id=UUID.fromString(order.getSourceRequestId()); }
        catch(IllegalArgumentException legacyReference) { return Optional.empty(); }
        return tickets.lock(id);
    }
    @Override public void before(OrdenTrabajo order, Action action) {
        projection(order).ifPresent(p -> {
            if(action==Action.CREATE && p.orderId()!=null)
                throw new InvalidStateTransitionException("El ticket ya tiene una orden vinculada");
            String status=p.snapshot().path("currentStatus").asString();
            // A cancellation does not undo an intervention already started; finishing it remains possible.
            boolean finishing=action==Action.COMPLETE || action==Action.VALIDATE || action==Action.PAUSE;
            if(java.util.Set.of("CANCELLED","CLOSED","RESOLVED","DUPLICATE").contains(status) && !finishing)
                throw new InvalidStateTransitionException("El ticket no permite nuevas acciones");
            if(action==Action.CREATE && p.routing()==null)
                throw new InvalidStateTransitionException("Falta la derivacion ROUTED del ticket");
        });
    }
    @Override public void beforeEdit(OrdenTrabajo existing, OrdenTrabajo proposed) {
        if(projection(existing).isPresent() && (proposed.getOrigin()!=existing.getOrigin()
                || !java.util.Objects.equals(existing.getSourceRequestId(), proposed.getSourceRequestId())))
            throw new InvalidStateTransitionException("No se puede reemplazar el ticket de una orden vinculada");
        if(projection(existing).isEmpty() && projection(proposed).isPresent())
            throw new InvalidStateTransitionException("Cree una orden vinculada al ticket mediante POST");
    }
    @Override public void after(OrdenTrabajo order, Action action) {
        projection(order).ifPresent(p -> {
            boolean started=p.started();
            String status=p.snapshot().path("currentStatus").asString();
            boolean terminal=java.util.Set.of("CANCELLED","CLOSED","RESOLVED","DUPLICATE").contains(status);
            if(!terminal && java.util.Set.of(Action.SCHEDULE,Action.START,Action.PAUSE,Action.COMPLETE).contains(action)) {
                // Enqueue STARTED first, under the same projection lock and transaction as PROGRESS.
                if(!started && status.equals("ROUTED")) {
                    publisher.publish(p.ticketId(),"STARTED",null,p.correlationId(),null);
                    started=true;
                }
                var content=json.createObjectNode();
                if(action==Action.COMPLETE) {
                    content.put("publicMessage","La intervencion de Obras Publicas ha finalizado.");
                    content.putObject("details").putObject("resolution").put("type","ACTION_COMPLETED");
                    publisher.publish(p.ticketId(),"RESOLVED",content,p.correlationId(),null);
                } else {
                    content.put("internalMessage", switch(action) {
                        case SCHEDULE -> "Intervencion programada para " + order.getScheduledDate();
                        case START -> "Intervencion iniciada";
                        default -> "Intervencion pausada";
                    });
                    publisher.publish(p.ticketId(),"PROGRESS",content,p.correlationId(),null);
                }
            }
            tickets.save(new Projection(p.ticketId(),p.snapshot(),p.updatedAt(),p.routing(),p.routingAt(),p.information(),
                    p.informationAt(),p.escalation(),p.escalationAt(),p.correlationId(),order.getId(),started,
                    action==Action.SCHEDULE?false:p.planningReviewRequired(),action==Action.CREATE?false:p.evaluationRequired()));
        });
    }
}
