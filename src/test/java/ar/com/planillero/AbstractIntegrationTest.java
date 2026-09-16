package ar.com.planillero;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base de las pruebas de integración.
 *
 * <p>Levanta un PostgreSQL real con Testcontainers en lugar de usar H2, para que el comportamiento
 * de la base sea el mismo que en producción. El contenedor es estático: se inicia una vez por JVM y
 * lo comparten todas las clases que heredan.
 */
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
