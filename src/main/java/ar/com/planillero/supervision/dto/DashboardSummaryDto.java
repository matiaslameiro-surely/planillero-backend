package ar.com.planillero.supervision.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Resumen consolidado de KPIs operativos para el tablero de supervisión central.
 */
public record DashboardSummaryDto(
        LocalDate date,
        String jurisdiction,
        int totalOperators,
        int inFieldOperators,
        int delayedOperators,
        int offlineOperators,
        int completedShiftOperators,
        int totalVisits,
        int pendingVisits,
        int inProgressVisits,
        int completedVisits,
        double slaComplianceRate,
        List<SupervisionExceptionDto> exceptions
) {}
