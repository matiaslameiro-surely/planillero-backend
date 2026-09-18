package ar.com.planillero.health;

/**
 * Representa el estado de salud y la latencia de la conexión a la base de datos.
 *
 * @param status    estado de conectividad del motor de persistencia ("UP" o "DOWN")
 * @param latencyMs tiempo de respuesta en milisegundos para una consulta de verificación liviana
 */
public record DatabaseHealthResponse(
        String status,
        long latencyMs
) {}
