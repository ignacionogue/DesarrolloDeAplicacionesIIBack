package com.example.demo.model;

/**
 * Flujo de estados de una OrdenTrabajo (punto 15). Tal como aclara la
 * consigna, este flujo es una PROPUESTA sujeta a ajustes:
 *
 *   PENDIENTE -> PROGRAMADA -> ASIGNADA -> EN_EJECUCION -> PAUSADA -> COMPLETADA -> VALIDADA
 *   COMPLETADA -> REABIERTA -> EN_EJECUCION   (si la validacion rechaza la OT)
 */
public enum EstadoOT {
    PENDIENTE,
    PROGRAMADA,
    ASIGNADA,
    EN_EJECUCION,
    PAUSADA,
    COMPLETADA,
    VALIDADA,
    REABIERTA
}
