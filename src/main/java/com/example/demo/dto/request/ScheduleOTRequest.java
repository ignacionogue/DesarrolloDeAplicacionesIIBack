package com.example.demo.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Body de PATCH /work-orders/{id}/schedule.
 * Si se envia 'crew', asigna esa cuadrilla. Omitirlo conserva la actual.
 * El resultado es ASIGNADA con cuadrilla, o PROGRAMADA sin ella.
 */
public class ScheduleOTRequest {

    @NotNull(message = "La fecha programada es obligatoria")
    private LocalDate scheduledDate;

    private String crew;

    public LocalDate getScheduledDate() {
        return scheduledDate;
    }

    public void setScheduledDate(LocalDate scheduledDate) {
        this.scheduledDate = scheduledDate;
    }

    public String getCrew() {
        return crew;
    }

    public void setCrew(String crew) {
        this.crew = crew;
    }
}
