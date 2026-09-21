package ar.com.planillero.planning.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import ar.com.planillero.planning.VisitStatus;
import ar.com.planillero.planning.VisitUrgency;

/**
 * Visita tal como llega a la grilla del supervisor.
 *
 * @param syncedDeferred el formulario de la visita llegó por sincronización diferida y no en línea
 * @param syncedAt       cuándo se recibió esa sincronización, o {@code null} si no la hubo
 */
public record VisitDto(
        UUID id,
        String code,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        VisitStatus status,
        VisitUrgency urgency,
        boolean syncedDeferred,
        Instant syncedAt) {
}