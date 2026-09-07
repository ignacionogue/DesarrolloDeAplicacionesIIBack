package com.example.demo.model;

/**
 * Estados del flujo de aprobacion de un ProyectoObra (DEFINIDO en la consigna).
 * Flujo valido:
 *   BORRADOR -> PENDIENTE_APROBACION -> APROBADO
 *   PENDIENTE_APROBACION -> RECHAZADO
 */
public enum EstadoAprobacion {
    BORRADOR,
    PENDIENTE_APROBACION,
    APROBADO,
    RECHAZADO
}
