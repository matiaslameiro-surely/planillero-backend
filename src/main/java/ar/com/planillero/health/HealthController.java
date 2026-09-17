package ar.com.planillero.health;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint de salud y diagnóstico operativo de la aplicación.
 *
 * <p>Expone el estado de los componentes del backend. Responde en {@code /health} como ruta
 * principal y preserva {@code /salud} para retrocompatibilidad con clientes existentes.
 */
@RestController
public class HealthController {

    private final DatabaseHealthService databaseHealthService;

    public HealthController(DatabaseHealthService databaseHealthService) {
        this.databaseHealthService = databaseHealthService;
    }

    /**
     * Consulta el estado de salud global del backend y la infraestructura de persistencia.
     *
     * @return objeto con el estado consolidado, marca de tiempo y métricas de base de datos
     */
    @GetMapping({"/health", "/salud"})
    public HealthResponse getHealth() {
        DatabaseHealthResponse databaseHealth = databaseHealthService.checkHealth();
        String overallStatus = "UP".equals(databaseHealth.status()) ? "UP" : "DEGRADED";
        return HealthResponse.of(
                overallStatus,
                Instant.now(),
                databaseHealth
        );
    }
}
