package com.example.demo.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Implementacion PROVISIONAL: solo deja constancia en el log de que el evento
 * se disparo. Sirve para no bloquear el desarrollo del resto del modulo
 * mientras se define el mecanismo real de mensajeria con Azure.
 *
 * Cuando ese mecanismo este definido, se reemplaza esta clase (o se agrega
 * una nueva implementacion de ProjectEventPublisher) sin modificar
 * ProyectoObraService.
 */
@Component
public class LoggingProjectEventPublisher implements ProjectEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingProjectEventPublisher.class);

    @Override
    public void publishProjectApproved(Long projectId, String projectName) {
        log.info("[EVENTO] publicWorksProjectApproved -> projectId={}, projectName='{}'", projectId, projectName);
    }
}
