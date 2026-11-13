package com.example.demo.dto.response;

import java.time.OffsetDateTime;

public record IntegrationEventResponse(
        String id,
        String direction,
        String eventType,
        String sourceModule,
        String status,
        OffsetDateTime occurredAt
) { }
