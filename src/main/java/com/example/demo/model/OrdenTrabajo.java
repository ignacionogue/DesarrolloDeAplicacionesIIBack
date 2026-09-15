package com.example.demo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * Representa una intervencion/trabajo concreto (NO un ProyectoObra general).
 * Ejemplo: "Reparar bache frente a escuela".
 *
 * 'cuadrilla' es una relacion real a la entidad Cuadrilla (migrado desde un
 * String simple que se uso en la Fase 5, cuando Cuadrilla todavia no existia).
 * El contrato de API no cambia: el Front sigue viendo 'crew' como string
 * (el nombre de la cuadrilla), resuelto en el mapper/service.
 *
 * 'hasEvidence' NO se persiste: se calcula en el Service a partir de la
 * existencia de registros en Evidencia (ver OrdenTrabajoService), siguiendo
 * el mismo criterio que 'budgetProgress' en ProyectoObra.
 */
@Entity
@Table(name = "orden_trabajo")
public class OrdenTrabajo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Referencia externa (ej. ticket de Atencion Ciudadana). Puede no existir si el origen es MANUAL. */
    @Column(length = 100)
    private String sourceRequestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrigenOT origin;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(length = 100)
    private String interventionType;

    @Column(length = 300)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PrioridadOT priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoOT status = EstadoOT.PENDIENTE;

    @ManyToOne
    @JoinColumn(name = "cuadrilla_id")
    private Cuadrilla cuadrilla;

    private LocalDate scheduledDate;

    private Integer estimatedDurationHours;

    @Column(length = 1000)
    private String outcome;

    protected OrdenTrabajo() {
        // JPA
    }

    public OrdenTrabajo(String sourceRequestId, OrigenOT origin, String description, String interventionType,
                         String location, PrioridadOT priority, Integer estimatedDurationHours, Cuadrilla cuadrilla) {
        this.sourceRequestId = sourceRequestId;
        this.origin = origin;
        this.description = description;
        this.interventionType = interventionType;
        this.location = location;
        this.priority = priority;
        this.estimatedDurationHours = estimatedDurationHours;
        this.cuadrilla = cuadrilla;
        // Si la OT nace con cuadrilla ya asignada, el status nace en ASIGNADA
        // (consistente con el ejemplo de contrato del punto 7, donde 'crew' y
        // 'status: ASIGNADA' aparecen juntos). Si no tiene cuadrilla, queda en
        // PENDIENTE (valor por defecto del campo).
        if (cuadrilla != null) {
            this.status = EstadoOT.ASIGNADA;
        }
    }

    public void updateDatosGenerales(String sourceRequestId, OrigenOT origin, String description,
                                      String interventionType, String location, PrioridadOT priority,
                                      Integer estimatedDurationHours, Cuadrilla cuadrilla) {
        this.sourceRequestId = sourceRequestId;
        this.origin = origin;
        this.description = description;
        this.interventionType = interventionType;
        this.location = location;
        this.priority = priority;
        this.estimatedDurationHours = estimatedDurationHours;
        this.cuadrilla = cuadrilla;
        ajustarStatusSegunCuadrilla();
    }

    /**
     * Mantiene 'status' coherente con la presencia/ausencia de cuadrilla,
     * pero solo mientras la OT esta en una etapa temprana. No interviene si
     * ya esta EN_EJECUCION, PAUSADA, COMPLETADA, VALIDADA o REABIERTA, para
     * no pisar un avance real con un simple PUT de datos generales.
     */
    private void ajustarStatusSegunCuadrilla() {
        if (this.status == EstadoOT.PENDIENTE || this.status == EstadoOT.PROGRAMADA
                || this.status == EstadoOT.ASIGNADA) {
            this.status = (this.cuadrilla != null) ? EstadoOT.ASIGNADA
                    : (this.scheduledDate != null ? EstadoOT.PROGRAMADA : EstadoOT.PENDIENTE);
        }
    }

    public void programar(LocalDate scheduledDate, Cuadrilla cuadrilla) {
        this.scheduledDate = scheduledDate;
        if (cuadrilla != null) {
            this.cuadrilla = cuadrilla;
            this.status = EstadoOT.ASIGNADA;
        } else {
            this.status = EstadoOT.PROGRAMADA;
        }
    }

    public void iniciar() {
        this.status = EstadoOT.EN_EJECUCION;
    }

    public void pausar() {
        this.status = EstadoOT.PAUSADA;
    }

    public void completar(String outcome) {
        this.status = EstadoOT.COMPLETADA;
        this.outcome = outcome;
    }

    public void validar(boolean aprobada) {
        this.status = aprobada ? EstadoOT.VALIDADA : EstadoOT.REABIERTA;
    }

    public Long getId() {
        return id;
    }

    public String getSourceRequestId() {
        return sourceRequestId;
    }

    public OrigenOT getOrigin() {
        return origin;
    }

    public String getDescription() {
        return description;
    }

    public String getInterventionType() {
        return interventionType;
    }

    public String getLocation() {
        return location;
    }

    public PrioridadOT getPriority() {
        return priority;
    }

    public EstadoOT getStatus() {
        return status;
    }

    public Cuadrilla getCuadrilla() {
        return cuadrilla;
    }

    public LocalDate getScheduledDate() {
        return scheduledDate;
    }

    public Integer getEstimatedDurationHours() {
        return estimatedDurationHours;
    }

    public String getOutcome() {
        return outcome;
    }
}
