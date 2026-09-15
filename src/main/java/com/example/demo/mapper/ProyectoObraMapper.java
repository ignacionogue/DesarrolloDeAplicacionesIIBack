package com.example.demo.mapper;

import com.example.demo.dto.request.ProyectoObraRequest;
import com.example.demo.dto.response.ProyectoObraResponse;
import com.example.demo.model.EstadoAprobacion;
import com.example.demo.model.ProyectoObra;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class ProyectoObraMapper {

    public ProyectoObra toEntity(ProyectoObraRequest request) {
        ProyectoObra proyecto = new ProyectoObra(
                request.getName(),
                request.getDescription(),
                request.getScope(),
                request.getLocation(),
                request.getEstimatedBudget(),
                request.getApprovedBudget(),
                request.getEstimatedStartDate(),
                request.getEstimatedDurationDays(),
                request.getApprovedDeadlineDays(),
                request.getTechnicalManager(),
                request.getContractor()
        );
        updateEntity(proyecto, request);
        return proyecto;
    }

    public void updateEntity(ProyectoObra proyecto, ProyectoObraRequest request) {
        proyecto.updateDatosGenerales(
                request.getName(),
                request.getDescription(),
                request.getScope(),
                request.getLocation(),
                request.getEstimatedBudget(),
                request.getApprovedBudget(),
                request.getUsedBudget(),
                request.getEstimatedStartDate(),
                request.getEstimatedDurationDays(),
                request.getApprovedDeadlineDays(),
                request.getPhysicalProgress(),
                request.getTechnicalManager(),
                request.getContractor()
        );
    }

    public ProyectoObraResponse toResponse(ProyectoObra proyecto) {
        return new ProyectoObraResponse(
                proyecto.getId(),
                proyecto.getName(),
                proyecto.getDescription(),
                proyecto.getScope(),
                proyecto.getLocation(),
                proyecto.getEstimatedBudget(),
                proyecto.getApprovedBudget(),
                proyecto.getEstimatedStartDate(),
                proyecto.getEstimatedDurationDays(),
                proyecto.getApprovedDeadlineDays(),
                proyecto.getPhysicalProgress(),
                calcularBudgetProgress(proyecto),
                resolverStatus(proyecto),
                proyecto.getTechnicalManager(),
                proyecto.getContractor()
        );
    }

    /**
     * Mientras el proyecto no esta APROBADO, el status visible es el de aprobacion
     * (BORRADOR / PENDIENTE_APROBACION / RECHAZADO). Una vez APROBADO, el status
     * visible pasa a ser el de ejecucion (activityStatus).
     */
    private String resolverStatus(ProyectoObra proyecto) {
        if (proyecto.getApprovalStatus() != EstadoAprobacion.APROBADO) {
            return proyecto.getApprovalStatus().name();
        }
        return proyecto.getActivityStatus() != null
                ? proyecto.getActivityStatus().name()
                : EstadoAprobacion.APROBADO.name();
    }

    private Integer calcularBudgetProgress(ProyectoObra proyecto) {
        BigDecimal approved = proyecto.getApprovedBudget();
        BigDecimal used = proyecto.getUsedBudget();
        if (approved == null || approved.compareTo(BigDecimal.ZERO) == 0 || used == null) {
            return 0;
        }
        return used.multiply(BigDecimal.valueOf(100))
                .divide(approved, 0, RoundingMode.HALF_UP)
                .intValue();
    }
}
