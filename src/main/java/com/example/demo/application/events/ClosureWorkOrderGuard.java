package com.example.demo.application.events;

import com.example.demo.model.OrdenTrabajo;
import com.example.demo.exception.InvalidStateTransitionException;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;

@Component
public class ClosureWorkOrderGuard implements WorkOrderEventHandler {
    private final ClosureIntegrationStore closures;
    public ClosureWorkOrderGuard(ClosureIntegrationStore closures) {this.closures=closures;}
    public void before(OrdenTrabajo order,Action action) {
        if(action!=Action.START) return;
        var all=closures.forOrder(order.getId());
        var byId=new HashMap<UUID,ClosureIntegrationStore.Closure>(); all.forEach(c->byId.put(c.requestId(),c));
        var allowed=new HashMap<UUID,Boolean>();
        var now=OffsetDateTime.now(ZoneOffset.UTC);
        for(var c:all) {
            var root=c;
            var visited=new HashSet<UUID>();
            while(root.request().hasNonNull("previousClosureRequestId")) {
                if(!visited.add(root.requestId())) throw new IllegalStateException("CLOSURE_EXTENSION_CYCLE");
                root=byId.get(UUID.fromString(root.request().path("previousClosureRequestId").asString()));
                if(root==null) throw new IllegalStateException("CLOSURE_EXTENSION_MISSING");
            }
            allowed.merge(root.requestId(),c.effectiveState(now).equals("ACTIVE"),Boolean::logicalOr);
        }
        if(allowed.containsValue(false)) throw new InvalidStateTransitionException("La orden requiere un corte autorizado dentro de su ventana vigente");
    }
    public void after(OrdenTrabajo order,Action action) { }
}
