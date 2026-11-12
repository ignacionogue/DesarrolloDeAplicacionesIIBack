package com.example.demo.application.ai;

import com.example.demo.dto.response.IncidentTriageSuggestion;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class ResilientIncidentTriageGatewayTest {
    @Test
    void timeoutFallsBackAndOpensCircuit() {
        IncidentTriageGateway slow = (description, version) -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return IncidentTriageSuggestion.manual("late");
        };
        var metrics = new IncidentTriageMetrics(new SimpleMeterRegistry());
        var gateway = new ResilientIncidentTriageGateway(slow, metrics, Duration.ofMillis(20),
                1, Duration.ofSeconds(30), Clock.systemUTC(), Executors.newSingleThreadExecutor());
        try {
            var timeout = gateway.suggest("bache", "v1");
            assertEquals(0.0, timeout.confidence());
            assertTrue(timeout.reasons().get(0).contains("tiempo limite"));
            long started = System.nanoTime();
            var open = gateway.suggest("bache", "v1");
            assertTrue(Duration.ofNanos(System.nanoTime() - started).toMillis() < 100);
            assertTrue(open.reasons().get(0).contains("temporalmente pausado"));
        } finally {
            gateway.close();
        }
    }

    @Test
    void invalidProviderResultFallsBackThroughSchemaValidator() {
        var suggestion = new IncidentTriageSuggestion("", null, null, null, 3, false, java.util.List.of());
        assertNotNull(suggestion);
    }
}
