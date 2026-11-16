package com.example.demo;

import com.example.demo.application.events.EventEnvelope;
import com.example.demo.application.events.EventTransport;
import com.example.demo.infrastructure.integration.SimulatedFailureEventTransport;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SimulatedFailureEventTransportTests {
    private EventEnvelope event() {
        return new EventEnvelope(UUID.randomUUID(), "streetClosureRequested", "1.0", OffsetDateTime.now(),
                "obras", UUID.randomUUID(), null, tools.jackson.databind.node.JsonNodeFactory.instance.objectNode());
    }

    @Test void enabledFailureStopsBeforeDelegateAndKeepsTheEnvelopeIdentity() {
        var calls = new AtomicInteger();
        EventTransport delegate = ignored -> calls.incrementAndGet();
        var transport = new SimulatedFailureEventTransport(delegate, true);
        var envelope = event();

        var failure = assertThrows(IllegalStateException.class, () -> transport.publish(envelope));

        assertEquals("SIMULATED_PUBLICATION_FAILURE", failure.getMessage());
        assertEquals(0, calls.get());
        assertNotNull(envelope.eventId());
    }

    @Test void disabledFailureDelegatesTheSameEnvelope() {
        var expected = event();
        var received = new java.util.concurrent.atomic.AtomicReference<EventEnvelope>();
        new SimulatedFailureEventTransport(received::set, false).publish(expected);
        assertSame(expected, received.get());
    }
}
