package ar.com.planillero.sync;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import ar.com.planillero.AbstractIntegrationTest;
import ar.com.planillero.VisitFixtures;
import ar.com.planillero.common.ApiException;
import ar.com.planillero.forms.dto.FormSubmissionRequest;
import ar.com.planillero.sync.dto.SyncBatchRequest;
import ar.com.planillero.sync.dto.SyncBatchResponse;
import ar.com.planillero.sync.dto.SyncOperationRequest;
import ar.com.planillero.sync.dto.SyncOperationStatus;
import tools.jackson.databind.ObjectMapper;

/**
 * La prueba que sostiene toda la tarea: que veinte envíos simultáneos dejen <strong>un</strong> acta.
 *
 * <p>Se llama al servicio directamente y no por HTTP, con hilos de verdad soltados a la vez: es la
 * única forma de que las veinte transacciones se pisen en la base. Corre contra el PostgreSQL de
 * Testcontainers, como el resto de los tests de integración — contra una base en memoria, el
 * comportamiento de un índice único bajo concurrencia no significaría nada.
 */
@SpringBootTest
class SyncConcurrencyIntegrationTest extends AbstractIntegrationTest {

    private static final int HILOS = 20;
    private static final String OPERADOR = "operador.demo";

    @Autowired
    private SyncService syncService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID visita;

    @BeforeEach
    void crearVisita() {
        visita = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into visits.visits
                    (id, code, address, latitude, longitude, jurisdiction, status, urgency)
                values (?, ?, 'Calle Ficticia 300', -34.600000, -58.400000, 'ZONA_NORTE', 'PENDING', 'LOW')
                """, visita, "C-" + visita.toString().substring(0, 8));
        VisitFixtures.assign(jdbcTemplate, visita, "operador.demo");
    }

    @Test
    @DisplayName("20 hilos con la misma clave de lote dejan un solo formulario")
    void mismaClaveEnVeinteHilos() throws Exception {
        UUID clave = UUID.randomUUID();
        SyncBatchRequest lote = lote(UUID.randomUUID());

        Resultados resultados = enParalelo(() -> syncService.process(clave, lote, OPERADOR));

        assertThat(formulariosGuardados())
                .as("el acta se escribió una sola vez pese a los %d envíos simultáneos", HILOS)
                .isEqualTo(1);

        assertThat(resultados.exitosas()).isNotEmpty();

        // Todo el que recibió una respuesta recibió LA MISMA respuesta: la del envío que ganó.
        assertThat(resultados.exitosas().stream()
                .map(objectMapper::writeValueAsString)
                .distinct())
                .hasSize(1);

        // Los que llegaron mientras el ganador procesaba reciben un 409 explícito, no una respuesta
        // a medias ni un duplicado.
        assertThat(resultados.errores())
                .allSatisfy(error -> assertThat(error.getCode()).isEqualTo("idempotency_key_in_progress"));
    }

    @Test
    @DisplayName("20 hilos con claves distintas y la misma operación dejan un solo formulario")
    void mismaOperacionConClavesDistintas() throws Exception {
        // El caso que la clave de lote NO cubre: el dispositivo reagrupa su cola y la misma operación
        // viaja en lotes distintos. Lo único que la frena es el índice único de `sync_operation_id`.
        UUID operacion = UUID.randomUUID();

        Resultados resultados = enParalelo(
                () -> syncService.process(UUID.randomUUID(), lote(operacion), OPERADOR));

        assertThat(formulariosGuardados()).isEqualTo(1);
        assertThat(resultados.errores()).isEmpty();
        assertThat(resultados.exitosas()).hasSize(HILOS);

        List<SyncOperationStatus> estados = resultados.exitosas().stream()
                .map(respuesta -> respuesta.results().getFirst().status())
                .toList();

        assertThat(estados).filteredOn(SyncOperationStatus.APPLIED::equals)
                .as("exactamente uno aplicó; el resto vio que ya estaba aplicada")
                .hasSize(1);
        assertThat(estados).filteredOn(SyncOperationStatus.DUPLICATE::equals).hasSize(HILOS - 1);
    }

    /** Lo que devolvieron los hilos: respuestas por un lado, rechazos de negocio por el otro. */
    private record Resultados(List<SyncBatchResponse> exitosas, List<ApiException> errores) {
    }

    /** Suelta {@link #HILOS} hilos a la vez sobre la misma acción y recoge lo que devolvió cada uno. */
    private Resultados enParalelo(Callable<SyncBatchResponse> accion) throws Exception {
        // Todos esperan en la misma barrera y salen juntos: sin esto, el primero termina antes de que
        // arranque el último y el test no probaría concurrencia alguna.
        CountDownLatch largada = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(HILOS)) {
            List<Future<SyncBatchResponse>> futuros = new java.util.ArrayList<>();
            for (int i = 0; i < HILOS; i++) {
                futuros.add(pool.submit(() -> {
                    largada.await();
                    return accion.call();
                }));
            }
            largada.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(60, TimeUnit.SECONDS))
                    .as("los %d hilos terminaron dentro del tiempo previsto", HILOS)
                    .isTrue();

            List<SyncBatchResponse> exitosas = new java.util.ArrayList<>();
            List<ApiException> errores = new java.util.ArrayList<>();
            for (Future<SyncBatchResponse> futuro : futuros) {
                try {
                    exitosas.add(futuro.get());
                } catch (java.util.concurrent.ExecutionException fallo) {
                    // Sólo se tolera el rechazo de negocio: cualquier otra excepción hace fallar el
                    // test, que es lo que corresponde si la concurrencia rompió algo.
                    if (fallo.getCause() instanceof ApiException rechazo) {
                        errores.add(rechazo);
                    } else {
                        throw fallo;
                    }
                }
            }
            return new Resultados(List.copyOf(exitosas), List.copyOf(errores));
        }
    }

    private SyncBatchRequest lote(UUID operacion) {
        FormSubmissionRequest form = new FormSubmissionRequest("mantenimiento-general", null,
                objectMapper.readTree("""
                        {"workedHours": 4, "taskType": "INSPECCION", "observations": "Control de rutina."}
                        """));
        return new SyncBatchRequest(List.of(
                new SyncOperationRequest(operacion, SyncOperationType.VISIT_FORM, visita, form)));
    }

    private int formulariosGuardados() {
        Integer total = jdbcTemplate.queryForObject(
                "select count(*) from visits.visits where id = ? and responses_json is not null",
                Integer.class, visita);
        return total == null ? 0 : total;
    }
}
