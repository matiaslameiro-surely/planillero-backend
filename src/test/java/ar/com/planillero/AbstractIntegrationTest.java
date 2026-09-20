package ar.com.planillero;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Clase base para pruebas de integracion que requieren infraestructura de persistencia.
 *
 * <p>Arranca un contenedor de PostgreSQL 16 y configura las propiedades de conexion mediante
 * {@link ServiceConnection}.
 *
 * <p>El contenedor se arranca a mano, una vez por JVM, y no con {@code @Testcontainers} +
 * {@code @Container}. Esas anotaciones lo apagan al terminar cada clase, pero Spring cachea el
 * contexto entre clases con la misma configuracion: la clase siguiente recibia un contexto que
 * apuntaba al puerto de un contenedor ya apagado y fallaba con {@code Connection refused}. Ademas,
 * surefire corre cada clase en su propio fork ({@code reuseForks=false} en el pom), asi que cada
 * clase tiene su propia base y no ve los datos que escriben las demas. Testcontainers (Ryuk) borra el
 * contenedor al terminar la JVM.
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    protected static final PostgreSQLContainer<?> postgres;

    static {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine");
        postgres.start();
    }
}
