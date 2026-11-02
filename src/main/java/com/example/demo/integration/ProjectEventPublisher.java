package com.example.demo.integration;

/**
 * Abstraccion para publicar eventos de dominio de Obras Publicas hacia afuera
 * del modulo. La implementacion usa la misma Outbox transaccional del modulo.
 */
public interface ProjectEventPublisher {

    /**
     * Evento disparado cuando un ProyectoObra pasa a estado APROBADO.
     * Nombre de evento acordado: "publicWorksProjectApproved".
     */
    void publishProjectApproved(com.example.demo.model.ProyectoObra project);
    void publishProjectCompleted(com.example.demo.model.ProyectoObra project);
}
