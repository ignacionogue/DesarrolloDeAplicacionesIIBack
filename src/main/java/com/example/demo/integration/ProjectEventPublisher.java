package com.example.demo.integration;

/**
 * Abstraccion para publicar eventos de dominio de Obras Publicas hacia afuera
 * del modulo. El mecanismo concreto (Azure Service Bus, Event Grid, etc.) lo
 * define mas adelante el integrante encargado de Azure, implementando esta
 * interfaz sin que el Service tenga que cambiar.
 */
public interface ProjectEventPublisher {

    /**
     * Evento disparado cuando un ProyectoObra pasa a estado APROBADO.
     * Nombre de evento acordado: "publicWorksProjectApproved".
     */
    void publishProjectApproved(Long projectId, String projectName);
}
