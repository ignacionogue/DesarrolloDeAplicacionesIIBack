package com.example.demo.infrastructure.integration;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.example.demo.application.events.EventEnvelope;

@Component
public class EventMetrics implements com.example.demo.application.events.EventTelemetry {
    private final MeterRegistry registry;
    public EventMetrics(MeterRegistry registry) { this.registry=registry; }
    public void result(String outcome, EventEnvelope event) {
        // No unbounded event IDs, module names or payload fields in metric labels.
        registry.counter("obras.events", "result", outcome).increment();
        if (event != null) LoggerFactory.getLogger(EventMetrics.class).atInfo()
                .addKeyValue("eventId", event.eventId()).addKeyValue("eventType", event.eventType())
                .addKeyValue("correlationId", event.correlationId()).addKeyValue("sourceModule", event.sourceModule())
                .addKeyValue("result", outcome).log("Integration event eventId={} eventType={} correlationId={} sourceModule={} result={}",
                        event.eventId(),event.eventType(),event.correlationId(),event.sourceModule(),outcome);
    }
}
