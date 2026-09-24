package ar.com.planillero.supervision.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;

/**
 * Solicitud de latido periódico (heartbeat) desde el cliente móvil.
 */
public record HeartbeatRequest(
        @DecimalMin(value = "0.0")
        @DecimalMax(value = "1.0")
        BigDecimal batteryLevel,

        // Los mismos valores que admite el CHECK de visits.operator_shifts.network_status: un valor
        // fuera de la lista se rechaza acá con 400 en vez de llegar a la base y terminar en 500.
        @Pattern(regexp = "ONLINE|OFFLINE|UNKNOWN", message = "debe ser ONLINE, OFFLINE o UNKNOWN")
        String networkStatus,

        BigDecimal latitude,

        BigDecimal longitude,

        String observations
) {}
