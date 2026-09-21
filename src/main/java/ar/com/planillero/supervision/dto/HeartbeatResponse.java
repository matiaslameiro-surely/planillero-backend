package ar.com.planillero.supervision.dto;

import java.time.Instant;

import ar.com.planillero.supervision.ShiftStatus;

/**
 * Respuesta a la recepción de latido.
 */
public record HeartbeatResponse(
        boolean success,
        Instant serverTimestamp,
        ShiftStatus status,
        String message
) {}
