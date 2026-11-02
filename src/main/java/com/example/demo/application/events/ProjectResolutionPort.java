package com.example.demo.application.events;

/** Application boundary for M1 case-file results linked by the public project UUID. */
public interface ProjectResolutionPort {
    boolean supports(String eventType, String eventVersion);
    void apply(EventEnvelope resolution);
}
