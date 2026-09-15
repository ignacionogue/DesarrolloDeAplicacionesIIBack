package com.example.demo;

import com.example.demo.model.*;
import com.example.demo.repository.*;
import com.example.demo.service.DashboardService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class DashboardEdgeTests {
    private final ProyectoObraRepository projects = mock(ProyectoObraRepository.class);
    private final OrdenTrabajoRepository orders = mock(OrdenTrabajoRepository.class);
    private final CuadrillaRepository crews = mock(CuadrillaRepository.class);
    private final DashboardService service = new DashboardService(projects, orders, crews);

    @Test void emptyDatabaseReturnsZeroWithoutDivisionErrors() {
        when(projects.findAll()).thenReturn(List.of());
        when(orders.findAll()).thenReturn(List.of());
        when(crews.findAll()).thenReturn(List.of());
        var result = service.summary();
        assertEquals(0, result.totalProjects());
        assertEquals(0, result.averagePhysicalProgress());
        assertEquals(0, result.budgetProgress());
        assertEquals(BigDecimal.ZERO, result.usedBudget());
        assertTrue(result.crewLoads().isEmpty());
    }
    @Test void projectDeadlinesAndRoundingFollowDocumentedRules() {
        var today = LocalDate.now();
        var overdue = project(today.minusDays(3), 2, 1, "3", "1", 25);
        overdue.aprobar();
        var dueToday = project(today.minusDays(2), 2, null, "0", "0", 26);
        dueToday.aprobar();
        var draft = project(today.minusDays(10), 1, null, null, "0", 0);
        when(projects.findAll()).thenReturn(List.of(overdue, dueToday, draft));
        when(orders.findAll()).thenReturn(List.of());
        when(crews.findAll()).thenReturn(List.of());
        var result = service.summary();
        assertEquals(2, result.activeProjects());
        assertEquals(1, result.delayedProjects());
        assertEquals(17, result.averagePhysicalProgress());
        assertEquals(33, result.budgetProgress());
        assertEquals(0, result.approvedBudget().compareTo(new BigDecimal("3")));
    }
    private ProyectoObra project(LocalDate start, int duration, Integer deadline, String approved, String used, int progress) {
        var p = new ProyectoObra("Test", null, null, null, BigDecimal.TEN,
                approved == null ? null : new BigDecimal(approved), start, duration, deadline, null, null);
        p.updateDatosGenerales("Test", null, null, null, BigDecimal.TEN,
                approved == null ? null : new BigDecimal(approved), new BigDecimal(used), start, duration,
                deadline, progress, null, null);
        return p;
    }
}
