package com.example.demo.dto.request;

import jakarta.validation.constraints.*;
import tools.jackson.databind.JsonNode;

public record TicketUpdateRequest(@NotNull Type updateType, @Size(max=10000) String publicMessage,
        @Size(max=10000) String internalMessage,@Min(0) @Max(100) Integer progress,JsonNode details,JsonNode attachments) {
    public enum Type { STARTED, PROGRESS, INFORMATION_REQUIRED, RETURNED, RESOLVED, REJECTED }
}
