package com.example.demo.service;

import com.example.demo.dto.response.DashboardSummaryResponse;
import com.example.demo.dto.response.DashboardSummaryResponse.CrewLoad;
import com.example.demo.model.*;
import com.example.demo.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

@Service
public class DashboardService {
    private final ProyectoObraRepository projects;
    private final OrdenTrabajoRepository orders;
    private final CuadrillaRepository crews;
    public DashboardService(ProyectoObraRepository projects, OrdenTrabajoRepository orders, CuadrillaRepository crews) {
        this.projects = projects; this.orders = orders; this.crews = crews;
    }
    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary() {
        var ps = projects.findAll();
        var os = orders.findAll();
        var today = LocalDate.now();
        var open = os.stream().filter(this::isOpen).toList();
        var approved = sum(ps, ProyectoObra::getApprovedBudget);
        var used = sum(ps, ProyectoObra::getUsedBudget);
        return new DashboardSummaryResponse(today, ps.size(), ps.stream().filter(this::isActive).count(),
                open.size(), os.stream().filter(o -> o.getOrigin() != OrigenOT.MANUAL).count(),
                open.stream().filter(o -> o.getScheduledDate() != null && o.getScheduledDate().isBefore(today)).count(),
                ps.stream().filter(this::isActive).filter(p -> p.getEstimatedStartDate().plusDays(
                        p.getApprovedDeadlineDays() != null ? p.getApprovedDeadlineDays() : p.getEstimatedDurationDays()
                ).isBefore(today)).count(),
                (int) Math.round(ps.stream().mapToInt(ProyectoObra::getPhysicalProgress).average().orElse(0)),
                sum(ps, ProyectoObra::getEstimatedBudget), approved, used,
                approved.signum() == 0 ? 0 : used.multiply(BigDecimal.valueOf(100)).divide(approved, 0, RoundingMode.HALF_UP).intValue(),
                crews.findAll().stream().map(c -> new CrewLoad(c.getId(), c.getNombre(),
                        open.stream().filter(o -> o.getCuadrilla() != null && o.getCuadrilla().getId().equals(c.getId())).count())).toList());
    }
    private boolean isOpen(OrdenTrabajo o) { return o.getStatus() != EstadoOT.COMPLETADA && o.getStatus() != EstadoOT.VALIDADA; }
    private boolean isActive(ProyectoObra p) {
        return p.getApprovalStatus() == EstadoAprobacion.APROBADO && p.getActivityStatus() != EstadoActividad.FINALIZADA;
    }
    private BigDecimal sum(List<ProyectoObra> list, Function<ProyectoObra, BigDecimal> value) {
        return list.stream().map(value).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
