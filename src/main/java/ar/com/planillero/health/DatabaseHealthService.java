package ar.com.planillero.health;

import java.sql.Connection;
import java.sql.Statement;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Servicio encargado de verificar la conectividad con la base de datos y medir la latencia.
 *
 * <p>Satisface la Heurística 1 de UX (visibilidad del estado del sistema) al medir el tiempo real
 * que demora una consulta de sondeo hacia PostgreSQL.
 */
@Service
public class DatabaseHealthService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseHealthService.class);
    private static final String VALIDATION_QUERY = "SELECT 1";

    private final DataSource dataSource;

    public DatabaseHealthService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Evalúa la conexión ejecutando una consulta liviana y calcula la latencia en milisegundos.
     *
     * @return resultado inmutable con el estado de conectividad y la latencia observada
     */
    public DatabaseHealthResponse checkHealth() {
        long startTime = System.currentTimeMillis();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(VALIDATION_QUERY);
            long latencyMs = System.currentTimeMillis() - startTime;
            return new DatabaseHealthResponse("UP", latencyMs);
        } catch (Exception ex) {
            long latencyMs = System.currentTimeMillis() - startTime;
            log.warn("Fallo al verificar la conectividad con la base de datos: {}", ex.getMessage());
            return new DatabaseHealthResponse("DOWN", latencyMs);
        }
    }
}
