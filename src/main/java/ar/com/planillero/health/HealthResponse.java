package ar.com.planillero.health;

import java.time.Instant;

/**
 * Representa el estado general de salud de la aplicación y sus subsistemas.
 *
 * @param status    estado global consolidado de la aplicación ("UP" o "DEGRADED")
 * @param timestamp momento exacto de la verificación
 * @param database  detalle de salud y métricas de latencia de la base de datos
 * @param estado    alias legado para retrocompatibilidad con clientes existentes ("ok" o "error")
 * @param momento   alias legado para retrocompatibilidad con clientes existentes (formato ISO-8601)
 */
public record HealthResponse(
        String status,
        Instant timestamp,
        DatabaseHealthResponse database,
        @Deprecated String estado,
        @Deprecated String momento
) {
    /**
     * Construye la respuesta de salud poblando automáticamente los campos legados de compatibilidad.
     *
     * @param status         estado consolidado ("UP" o "DEGRADED")
     * @param timestamp      marca de tiempo de la consulta
     * @param databaseHealth estado y latencia de la base de datos
     * @return instancia de {@link HealthResponse}
     */
    public static HealthResponse of(String status, Instant timestamp, DatabaseHealthResponse databaseHealth) {
        String legacyEstado = "UP".equals(status) ? "ok" : "error";
        return new HealthResponse(
                status,
                timestamp,
                databaseHealth,
                legacyEstado,
                timestamp.toString()
        );
    }
}
