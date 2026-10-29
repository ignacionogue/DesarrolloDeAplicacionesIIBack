package com.example.demo.application.events;
import com.example.demo.model.OrdenTrabajo;
public interface EnvironmentEventPublisher {
    void publish(OrdenTrabajo order, EnvironmentCaseStore.Case source, WorkOrderEventHandler.Action action);
}
