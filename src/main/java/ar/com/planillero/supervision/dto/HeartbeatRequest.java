package ar.com.planillero.supervision.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

/**
 * Solicitud de latido periódico (heartbeat) desde el cliente móvil.
 */
public record HeartbeatRequest(
        @DecimalMin(value = "0.0")
        @DecimalMax(value = "1.0")
        BigDecimal batteryLevel,

        String networkStatus,

        BigDecimal latitude,

        BigDecimal longitude,

        String observations
) {}
