package com.example.demo.dto.request;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

public record StreetClosureRequest(
        @NotNull @Positive Long workOrderId,
        @NotBlank @Size(max = 300) String location,
        @NotEmpty @Size(max = 50) List<@NotBlank @Size(max = 300) String> affectedSections,
        @NotNull LocalDate requestedFrom,
        @NotNull LocalDate requestedTo,
        @NotBlank @Size(max = 1000) String reason
) {}
