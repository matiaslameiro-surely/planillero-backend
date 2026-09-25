package ar.com.planillero.planning.dto;

import java.time.Instant;
import java.util.UUID;

import ar.com.planillero.planning.VisitStatus;

/**
 * Resultado de la finalización de una visita pericial.
 *
 * @param visitId     identificador de la visita
 * @param status      nuevo estado de la visita ({@link VisitStatus#COMPLETED})
 * @param code        código unívoco de la visita
 * @param completedAt timestamp del servidor en que se completó la visita
 */
public record CompleteVisitResponse(
        UUID visitId,
        VisitStatus status,
        String code,
        Instant completedAt) {
}
