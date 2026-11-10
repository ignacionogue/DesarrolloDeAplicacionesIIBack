package com.example.demo.application.events;

import com.example.demo.model.OrdenTrabajo;
import com.example.demo.model.OrigenOT;
import com.example.demo.exception.InvalidStateTransitionException;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class EnvironmentWorkOrderEvents implements WorkOrderEventHandler {
    private final EnvironmentCaseStore cases;
    private final EnvironmentEventPublisher publisher;
    public EnvironmentWorkOrderEvents(EnvironmentCaseStore cases, EnvironmentEventPublisher publisher) { this.cases=cases; this.publisher=publisher; }
    public void before(OrdenTrabajo order, Action action) {
        if(order.getOrigin()!=OrigenOT.AMBIENTE) return;
        var source=cases.lock(UUID.fromString(order.getSourceRequestId())).orElseThrow();
        if(action==Action.CREATE && source.orderId()!=null) throw new InvalidStateTransitionException("La solicitud de Ambiente ya tiene una orden");
    }
    public void beforeEdit(OrdenTrabajo existing, OrdenTrabajo proposed) {
        if((existing.getOrigin()==OrigenOT.AMBIENTE || proposed.getOrigin()==OrigenOT.AMBIENTE)
                && (existing.getOrigin()!=proposed.getOrigin() || !java.util.Objects.equals(existing.getSourceRequestId(),proposed.getSourceRequestId())))
            throw new InvalidStateTransitionException("No se puede sustituir el origen de una orden vinculada a Ambiente");
    }
    public void after(OrdenTrabajo order, Action action) {
        if(order.getOrigin()!=OrigenOT.AMBIENTE) return;
        var id=UUID.fromString(order.getSourceRequestId());
        var source=cases.lock(id).orElseThrow();
        if(action==Action.CREATE) cases.link(id,order.getId());
        if(source.eventType().equals("infrastructureRepairRequested") && (action==Action.SCHEDULE || action==Action.COMPLETE))
            publisher.publish(order,source,action);
    }
}
