package ar.com.planillero.planning.dto;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * Pedido de inicio de una visita, con la ubicación que capturó el dispositivo.
 *
 * @param latitude        grados, entre -90 y 90
 * @param longitude       grados, entre -180 y 180
 * @param accuracyMeters  radio de incertidumbre de la lectura GPS, en metros
 * @param clientTimestamp hora del reloj del dispositivo al capturar la ubicación
 */
public record StartVisitRequest(
        @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
        // El tope evita que un valor absurdo desborde la columna numeric(8,2) y termine en un 500.
        @NotNull @DecimalMin("0") @DecimalMax("999999") BigDecimal accuracyMeters,
        @NotNull Instant clientTimestamp) {
}
