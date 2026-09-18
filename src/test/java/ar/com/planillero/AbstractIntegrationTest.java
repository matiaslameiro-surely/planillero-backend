package ar.com.planillero;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Clase base para pruebas de integracion que requieren infraestructura de persistencia.
 *
 * <p>Usa un unico contenedor de PostgreSQL 16 para toda la corrida, y configura las propiedades de
 * conexion mediante {@link ServiceConnection}.
 *
 * <p>El contenedor se arranca una sola vez, a mano, y no con {@code @Testcontainers} +
 * {@code @Container}. Esas anotaciones lo apagan al terminar cada clase de test, pero Spring cachea
 * el contexto entre clases con la misma configuracion: la segunda clase recibia un contexto que
 * apuntaba al puerto de un contenedor ya apagado y fallaba con {@code Connection refused}. Con un
 * contenedor unico, el contexto cacheado y la base viven lo mismo. Testcontainers (Ryuk) lo borra al
 * terminar la JVM.
 *
 * <p>Consecuencia: las clases comparten la base. Un test que modifica datos del seed tiene que usar
 * registros que ningun otro test toque.
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    protected static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        postgres.start();
    }
}
