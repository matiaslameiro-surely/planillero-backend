package ar.com.planillero.planning.dto;

import java.math.BigDecimal;
import java.util.UUID;

import ar.com.planillero.planning.VisitStatus;
import ar.com.planillero.planning.VisitUrgency;

/** Visita tal como llega a la grilla del supervisor. */
public record VisitDto(
        UUID id,
        String code,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        VisitStatus status,
        VisitUrgency urgency) {
}