package com.example.demo.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.*;

/** Explicit offset timestamps; the legacy date-only endpoint is preserved. */
public record IntegratedClosureRequest(@NotNull @Positive Long workOrderId,
        @NotBlank @Size(max=1000) String reason,
        @NotEmpty @Size(max=50) List<@NotNull @Valid Section> affectedSections,
        @NotNull OffsetDateTime requestedFrom, @NotNull OffsetDateTime requestedTo,
        @NotBlank @Size(max=60) String closureType, UUID previousClosureRequestId) {
    public record Section(@NotBlank @Size(max=300) String street,@NotBlank @Size(max=300) String from,
            @NotBlank @Size(max=300) String to,@NotNull Direction direction) { }
    public enum Direction { FORWARD, REVERSE, BOTH }
}
