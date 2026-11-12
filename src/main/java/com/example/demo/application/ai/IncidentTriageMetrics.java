package com.example.demo.application.ai;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class IncidentTriageMetrics {
    private final MeterRegistry registry;

    public IncidentTriageMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(String outcome, Duration duration) {
        registry.counter("obras.ai.triage.requests", "outcome", outcome).increment();
        Timer.builder("obras.ai.triage.duration")
                .tag("outcome", outcome)
                .register(registry)
                .record(duration);
    }
}
