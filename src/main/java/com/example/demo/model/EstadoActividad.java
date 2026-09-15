package com.example.demo.model;

/**
 * Estados de ejecucion de un ProyectoObra, aplicables solo una vez que
 * el proyecto esta APROBADO.
 *
 * PROPUESTO: la consigna no cierra este listado (punto 12: "tambien habra
 * estados relacionados con la ejecucion de la obra"). Se propone este set
 * minimo, consistente con el ejemplo de contrato ("status": "EN_EJECUCION").
 * Al estar aislado en su propio enum, ampliarlo o modificarlo no impacta
 * el resto del modelo.
 */
public enum EstadoActividad {
    SIN_INICIAR,
    EN_EJECUCION,
    PAUSADA,
    FINALIZADA
}
