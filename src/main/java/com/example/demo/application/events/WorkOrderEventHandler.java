package com.example.demo.application.events;

import com.example.demo.model.OrdenTrabajo;

/** Business integration hooks; implementations join the existing service transaction. */
public interface WorkOrderEventHandler {
    enum Action { CREATE, EDIT, SCHEDULE, START, PAUSE, COMPLETE, VALIDATE }
    void before(OrdenTrabajo order, Action action);
    void after(OrdenTrabajo order, Action action);
    default void beforeEdit(OrdenTrabajo existing, OrdenTrabajo proposed) { }
}
