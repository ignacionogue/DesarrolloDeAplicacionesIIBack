package com.example.demo.application.events;

/** Future application boundary. No M1 DTO or event-name alias is accepted yet. */
public interface ProjectResolutionPort {
    boolean supports(String eventType, String eventVersion);
    void apply(EventEnvelope resolution);
}
