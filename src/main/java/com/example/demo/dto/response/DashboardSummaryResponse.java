package com.example.demo.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DashboardSummaryResponse(LocalDate asOfDate, long totalProjects, long activeProjects,
        long openWorkOrders, long externalWorkOrders, long delayedWorkOrders, long delayedProjects,
        int averagePhysicalProgress, BigDecimal estimatedBudget, BigDecimal approvedBudget,
        BigDecimal usedBudget, int budgetProgress, List<CrewLoad> crewLoads) {
    public record CrewLoad(Long crewId, String name, long openWorkOrders) {}
}
