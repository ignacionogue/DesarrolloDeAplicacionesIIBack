package com.example.demo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Representa una obra/proyecto general (NO una intervencion puntual: eso es OrdenTrabajo).
 * Ejemplo: "Repavimentacion de Av. Lima".
 *
 * Los nombres de campo siguen el contrato de API definido en la consigna (punto 4)
 * para minimizar la friccion de mapeo; igual se mantiene separada de los DTOs
 * para no acoplar el Front a esta clase.
 */
@Entity
@Table(name = "proyecto_obra")
public class ProyectoObra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(length = 1000)
    private String scope;

    @Column(length = 300)
    private String location;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal estimatedBudget;

    @Column(precision = 15, scale = 2)
    private BigDecimal approvedBudget;

    /** Presupuesto efectivamente ejecutado. No se expone directo en el contrato; se usa para derivar budgetProgress. */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal usedBudget = BigDecimal.ZERO;

    @Column(nullable = false)
    private LocalDate estimatedStartDate;

    @Column(nullable = false)
    private Integer estimatedDurationDays;

    private Integer approvedDeadlineDays;

    /**
     * PROVISIONAL: persistido como valor simple por ahora. Cuando se implementen
     * Etapa/Hito (punto 6), es candidato a transformarse en un valor calculado
     * a partir de esas entidades, sin romper el contrato de API expuesto.
     */
    @Column(nullable = false)
    private Integer physicalProgress = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoAprobacion approvalStatus = EstadoAprobacion.BORRADOR;

    /** Solo tiene valor una vez que approvalStatus == APROBADO. */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private EstadoActividad activityStatus;

    @Column(length = 200)
    private String technicalManager;

    @Column(length = 200)
    private String contractor;

    protected ProyectoObra() {
        // JPA
    }

    public ProyectoObra(String name, String description, String scope, String location,
                         BigDecimal estimatedBudget, BigDecimal approvedBudget,
                         LocalDate estimatedStartDate, Integer estimatedDurationDays,
                         Integer approvedDeadlineDays, String technicalManager, String contractor) {
        this.name = name;
        this.description = description;
        this.scope = scope;
        this.location = location;
        this.estimatedBudget = estimatedBudget;
        this.approvedBudget = approvedBudget;
        this.estimatedStartDate = estimatedStartDate;
        this.estimatedDurationDays = estimatedDurationDays;
        this.approvedDeadlineDays = approvedDeadlineDays;
        this.technicalManager = technicalManager;
        this.contractor = contractor;
    }

    // --- Actualizacion de datos generales (usado por PUT). No toca estados. ---
    public void updateDatosGenerales(String name, String description, String scope, String location,
                                      BigDecimal estimatedBudget, BigDecimal approvedBudget,
                                      BigDecimal usedBudget, LocalDate estimatedStartDate,
                                      Integer estimatedDurationDays, Integer approvedDeadlineDays,
                                      Integer physicalProgress, String technicalManager, String contractor) {
        this.name = name;
        this.description = description;
        this.scope = scope;
        this.location = location;
        this.estimatedBudget = estimatedBudget;
        this.approvedBudget = approvedBudget;
        if (usedBudget != null) {
            this.usedBudget = usedBudget;
        }
        this.estimatedStartDate = estimatedStartDate;
        this.estimatedDurationDays = estimatedDurationDays;
        this.approvedDeadlineDays = approvedDeadlineDays;
        if (physicalProgress != null) {
            this.physicalProgress = physicalProgress;
        }
        this.technicalManager = technicalManager;
        this.contractor = contractor;
    }

    public void marcarPendienteAprobacion() {
        this.approvalStatus = EstadoAprobacion.PENDIENTE_APROBACION;
    }

    public void aprobar() {
        this.approvalStatus = EstadoAprobacion.APROBADO;
        this.activityStatus = EstadoActividad.SIN_INICIAR;
    }

    public void rechazar() {
        this.approvalStatus = EstadoAprobacion.RECHAZADO;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getScope() {
        return scope;
    }

    public String getLocation() {
        return location;
    }

    public BigDecimal getEstimatedBudget() {
        return estimatedBudget;
    }

    public BigDecimal getApprovedBudget() {
        return approvedBudget;
    }

    public BigDecimal getUsedBudget() {
        return usedBudget;
    }

    public LocalDate getEstimatedStartDate() {
        return estimatedStartDate;
    }

    public Integer getEstimatedDurationDays() {
        return estimatedDurationDays;
    }

    public Integer getApprovedDeadlineDays() {
        return approvedDeadlineDays;
    }

    public Integer getPhysicalProgress() {
        return physicalProgress;
    }

    public EstadoAprobacion getApprovalStatus() {
        return approvalStatus;
    }

    public EstadoActividad getActivityStatus() {
        return activityStatus;
    }

    public String getTechnicalManager() {
        return technicalManager;
    }

    public String getContractor() {
        return contractor;
    }
}
