package com.example.demo.integration;

/**
 * Abstraccion para publicar eventos de dominio de Obras Publicas hacia afuera
 * del modulo. M1 permanece deshabilitado hasta confirmar su contrato bilateral;
 * el adaptador definitivo utilizara la misma Outbox transaccional del modulo.
 */
public interface ProjectEventPublisher {

    /**
     * Evento disparado cuando un ProyectoObra pasa a estado APROBADO.
     * Nombre de evento acordado: "publicWorksProjectApproved".
     */
    void publishProjectApproved(Long projectId, String projectName);
    void publishProjectCompleted(Long projectId);
}
