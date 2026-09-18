package ar.com.planillero.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import javax.sql.DataSource;

import ar.com.planillero.AbstractIntegrationTest;
import ar.com.planillero.health.DatabaseHealthResponse;
import ar.com.planillero.health.DatabaseHealthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Pruebas de integracion sobre PostgreSQL 16 con Flyway y HikariCP.
 *
 * <p>Verifica la ejecucion de migraciones, la creacion de esquemas logicos, la disponibilidad de
 * extensiones criptograficas y las metricas de latencia de salud.
 */
class DatabaseIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private DatabaseHealthService databaseHealthService;

    @Test
    @DisplayName("El servicio de salud de base de datos reporta estado UP y latencia positiva")
    void shouldReportDatabaseHealthUpWithPositiveLatency() {
        DatabaseHealthResponse response = databaseHealthService.checkHealth();

        assertThat(response.status()).isEqualTo("UP");
        assertThat(response.latencyMs()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    @DisplayName("Flyway crea y garantiza los esquemas logicos definidos en la migracion V1")
    void shouldCreateAllConfiguredLogicalSchemas() throws Exception {
        Set<String> schemas = new HashSet<>();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT schema_name FROM information_schema.schemata")) {
            while (rs.next()) {
                schemas.add(rs.getString("schema_name"));
            }
        }

        assertThat(schemas).contains("core", "visits", "forms", "audit");
    }

    @Test
    @DisplayName("La extension pgcrypto esta habilitada en PostgreSQL")
    void shouldHavePgcryptoExtensionInstalled() throws Exception {
        boolean extensionFound = false;
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT extname FROM pg_extension WHERE extname = 'pgcrypto'")) {
            if (rs.next()) {
                extensionFound = true;
            }
        }

        assertThat(extensionFound).isTrue();
    }

    @Test
    @DisplayName("Permite almacenar y consultar datos estructurados JSONB en PostgreSQL")
    void shouldSupportJsonbOperations() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TEMPORARY TABLE temp_form_data (id SERIAL PRIMARY KEY, payload JSONB)");
            statement.execute("INSERT INTO temp_form_data (payload) VALUES ('{\"type\": \"audit\", \"version\": 1}')");

            try (ResultSet rs = statement.executeQuery("SELECT payload->>'type' AS form_type FROM temp_form_data")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("form_type")).isEqualTo("audit");
            }
        }
    }
}
