package com.example.demo.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cuerpo esperado para POST /api/public-works/projects y PUT /api/public-works/projects/{id}.
 * No incluye 'status': los cambios de estado se hacen via los endpoints
 * submit-approval / approve / reject, no editando el recurso directamente.
 */
public class ProyectoObraRequest {

    @NotBlank(message = "El nombre es obligatorio")
    private String name;

    private String description;

    private String scope;

    private String location;

    @NotNull(message = "El presupuesto estimado es obligatorio")
    @DecimalMin(value = "0.0", inclusive = false, message = "El presupuesto estimado debe ser mayor a 0")
    private BigDecimal estimatedBudget;

    @DecimalMin(value = "0.0", message = "El presupuesto aprobado no puede ser negativo")
    private BigDecimal approvedBudget;

    @DecimalMin(value = "0.0", message = "El presupuesto utilizado no puede ser negativo")
    private BigDecimal usedBudget;

    @NotNull(message = "La fecha estimada de inicio es obligatoria")
    private LocalDate estimatedStartDate;

    @NotNull(message = "La duracion estimada es obligatoria")
    @Positive(message = "La duracion estimada debe ser mayor a 0")
    private Integer estimatedDurationDays;

    @Positive(message = "El plazo aprobado debe ser mayor a 0")
    private Integer approvedDeadlineDays;

    @Min(value = 0, message = "El avance fisico no puede ser negativo")
    @Max(value = 100, message = "El avance fisico no puede superar 100")
    private Integer physicalProgress;

    private String technicalManager;

    private String contractor;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public BigDecimal getEstimatedBudget() {
        return estimatedBudget;
    }

    public void setEstimatedBudget(BigDecimal estimatedBudget) {
        this.estimatedBudget = estimatedBudget;
    }

    public BigDecimal getApprovedBudget() {
        return approvedBudget;
    }

    public void setApprovedBudget(BigDecimal approvedBudget) {
        this.approvedBudget = approvedBudget;
    }

    public BigDecimal getUsedBudget() {
        return usedBudget;
    }

    public void setUsedBudget(BigDecimal usedBudget) {
        this.usedBudget = usedBudget;
    }

    public LocalDate getEstimatedStartDate() {
        return estimatedStartDate;
    }

    public void setEstimatedStartDate(LocalDate estimatedStartDate) {
        this.estimatedStartDate = estimatedStartDate;
    }

    public Integer getEstimatedDurationDays() {
        return estimatedDurationDays;
    }

    public void setEstimatedDurationDays(Integer estimatedDurationDays) {
        this.estimatedDurationDays = estimatedDurationDays;
    }

    public Integer getApprovedDeadlineDays() {
        return approvedDeadlineDays;
    }

    public void setApprovedDeadlineDays(Integer approvedDeadlineDays) {
        this.approvedDeadlineDays = approvedDeadlineDays;
    }

    public Integer getPhysicalProgress() {
        return physicalProgress;
    }

    public void setPhysicalProgress(Integer physicalProgress) {
        this.physicalProgress = physicalProgress;
    }

    public String getTechnicalManager() {
        return technicalManager;
    }

    public void setTechnicalManager(String technicalManager) {
        this.technicalManager = technicalManager;
    }

    public String getContractor() {
        return contractor;
    }

    public void setContractor(String contractor) {
        this.contractor = contractor;
    }
}
