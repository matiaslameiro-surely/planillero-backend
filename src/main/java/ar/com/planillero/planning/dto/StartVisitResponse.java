package ar.com.planillero.planning.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import ar.com.planillero.planning.VisitStatus;

/**
 * Resultado del inicio de una visita: lo que quedó persistido.
 *
 * <p>Las coordenadas y la precisión se devuelven con la escala con la que se guardaron, así la
 * pantalla del operador muestra exactamente lo que registró el servidor.
 *
 * @param startedAtDevice hora del reloj del dispositivo, tal como la informó
 * @param startedAtServer hora del reloj del servidor al recibir el pedido
 * @param driftSeconds    {@code startedAtServer - startedAtDevice}, en segundos, con signo
 */
public record StartVisitResponse(
        UUID visitId,
        VisitStatus status,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal accuracyMeters,
        Instant startedAtDevice,
        Instant startedAtServer,
        long driftSeconds) {
}
