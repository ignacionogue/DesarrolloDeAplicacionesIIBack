package com.example.demo.infrastructure.integration;

import com.example.demo.application.events.EventEnvelope;
import com.example.demo.application.events.EventTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Test-only decorator used to prove Outbox recovery without touching the shared broker. */
public final class SimulatedFailureEventTransport implements EventTransport {
    private static final Logger LOG = LoggerFactory.getLogger(SimulatedFailureEventTransport.class);
    private final EventTransport delegate;
    private final boolean failureEnabled;

    public SimulatedFailureEventTransport(EventTransport delegate, boolean failureEnabled) {
        this.delegate = delegate;
        this.failureEnabled = failureEnabled;
    }

    @Override
    public void publish(EventEnvelope event) {
        if (failureEnabled) {
            LOG.atWarn()
                    .addKeyValue("eventId", event.eventId())
                    .addKeyValue("eventType", event.eventType())
                    .addKeyValue("result", "simulated_publication_failure")
                    .log("Simulated outbound messaging failure");
            throw new IllegalStateException("SIMULATED_PUBLICATION_FAILURE");
        }
        delegate.publish(event);
    }
}
