package ar.com.planillero.supervision.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Excepción o alerta operativa destacada en el tablero de supervisión.
 */
public record SupervisionExceptionDto(
        UUID operatorId,
        String operatorUsername,
        String type,
        String severity,
        String message,
        Instant detectedAt
) {}
