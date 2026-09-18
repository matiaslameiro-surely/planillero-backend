package ar.com.planillero;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Clase base para pruebas de integracion que requieren infraestructura de persistencia.
 *
 * <p>Inicializa un contenedor Docker de PostgreSQL 16 reutilizable y configura automaticamente las
 * propiedades de conexion mediante {@link ServiceConnection}.
 */
@SpringBootTest
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    protected static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");
}
