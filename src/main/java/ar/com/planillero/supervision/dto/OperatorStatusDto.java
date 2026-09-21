package ar.com.planillero.supervision.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import ar.com.planillero.supervision.ShiftStatus;

/**
 * Estado en vivo de un operador en el tablero de supervisión.
 */
public record OperatorStatusDto(
        UUID operatorId,
        String username,
        String jurisdiction,
        ShiftStatus status,
        BigDecimal batteryLevel,
        String networkStatus,
        Instant lastHeartbeatAt,
        BigDecimal lastLatitude,
        BigDecimal lastLongitude,
        int assignedVisitsCount,
        int completedVisitsCount,
        String activeVisitCode,
        String activeVisitAddress,
        Long activeVisitElapsedMinutes,
        String slaStatus,
        String observations
) {}
