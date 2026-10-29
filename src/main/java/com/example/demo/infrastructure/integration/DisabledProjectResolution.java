package com.example.demo.infrastructure.integration;

import com.example.demo.application.events.*;
import org.springframework.stereotype.Component;

@Component
public class DisabledProjectResolution implements ProjectResolutionPort {
    public boolean supports(String eventType,String eventVersion) { return false; }
    public void apply(EventEnvelope resolution) { throw new IllegalStateException("M1_CONTRACT_NOT_CONFIRMED"); }
}
