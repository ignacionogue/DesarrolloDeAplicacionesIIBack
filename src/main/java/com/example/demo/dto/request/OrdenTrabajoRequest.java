package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;

public class OrdenTrabajoRequest {

    @Positive
    @Schema(description = "Obligatorio para origin PROYECTO; debe omitirse para los demas origenes")
    private Long projectId;

    public Long getProjectId() { return projectId; }

    public void setProjectId(Long projectId) { this.projectId = projectId; }

    @Size(max = 100)
    private String sourceRequestId;

    @NotBlank(message = "El origen es obligatorio")
    @Schema(allowableValues = {"MANUAL", "PROYECTO", "ATENCION_CIUDADANA", "INSPECCION"})
    private String origin;

    @NotBlank(message = "La descripcion es obligatoria")
    @Size(max = 1000)
    private String description;

    @Size(max = 100)
    private String interventionType;

    @Size(max = 300)
    private String location;

    @NotBlank(message = "La prioridad es obligatoria")
    private String priority;

    @Positive(message = "La duracion estimada debe ser mayor a 0")
    private Integer estimatedDurationHours;

    private String crew;

    public String getSourceRequestId() {
        return sourceRequestId;
    }

    public void setSourceRequestId(String sourceRequestId) {
        this.sourceRequestId = sourceRequestId;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getInterventionType() {
        return interventionType;
    }

    public void setInterventionType(String interventionType) {
        this.interventionType = interventionType;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public Integer getEstimatedDurationHours() {
        return estimatedDurationHours;
    }

    public void setEstimatedDurationHours(Integer estimatedDurationHours) {
        this.estimatedDurationHours = estimatedDurationHours;
    }

    public String getCrew() {
        return crew;
    }

    public void setCrew(String crew) {
        this.crew = crew;
    }
}
