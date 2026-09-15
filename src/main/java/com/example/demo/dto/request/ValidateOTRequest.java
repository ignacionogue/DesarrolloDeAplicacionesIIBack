package com.example.demo.dto.request;

import jakarta.validation.constraints.NotNull;

public class ValidateOTRequest {

    @NotNull(message = "Debe indicarse si la OT fue aprobada o no")
    private Boolean approved;

    private String observations;

    public Boolean getApproved() {
        return approved;
    }

    public void setApproved(Boolean approved) {
        this.approved = approved;
    }

    public String getObservations() {
        return observations;
    }

    public void setObservations(String observations) {
        this.observations = observations;
    }
}
