package ar.com.planillero.health;

import java.time.Instant;

/**
 * Representa el estado general de salud de la aplicación y sus subsistemas.
 *
 * @param status    estado global consolidado de la aplicación ("UP" o "DEGRADED")
 * @param timestamp momento exacto de la verificación
 * @param database  detalle de salud y métricas de latencia de la base de datos
 */
public record HealthResponse(
        String status,
        Instant timestamp,
        DatabaseHealthResponse database
) {}
