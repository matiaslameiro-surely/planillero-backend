package ar.com.planillero.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import ar.com.planillero.AbstractIntegrationTest;
import ar.com.planillero.sync.dto.SyncBatchRequest;
import com.jayway.jsonpath.JsonPath;
import tools.jackson.databind.ObjectMapper;

/**
 * Recuperación de una clave que quedó reservada y nunca se cerró.
 *
 * <p>El caso ocurre cuando el proceso se cae —o la base falla— entre que la clave se reserva y que
 * se guarda la respuesta. Sin recuperación, esa clave queda {@code IN_PROGRESS} para siempre y el
 * dispositivo recibe {@code 409} en cada reintento: su lote no se aplica nunca y el operador ve su
 * cola trabada sin ninguna explicación.
 *
 * <p>Retomarla es seguro por la <strong>otra</strong> garantía: la idempotencia por operación. Lo que
 * el lote alcanzó a aplicar antes de la caída vuelve como {@code DUPLICATE}, así que reprocesarlo no
 * puede duplicar un acta.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SyncAbandonedKeyIntegrationTest extends AbstractIntegrationTest {

    private static final String BATCH = "/api/v1/sync/batch";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID visita;

    @BeforeEach
    void crearVisita() {
        visita = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into visits.visits
                    (id, code, address, latitude, longitude, jurisdiction, status, urgency)
                values (?, ?, 'Calle Ficticia 400', -34.600000, -58.400000, 'ZONA_NORTE', 'PENDING', 'LOW')
                """, visita, "A-" + visita.toString().substring(0, 8));
    }

    @Test
    @DisplayName("una reserva abandonada se retoma y el lote se aplica")
    void reservaAbandonadaSeRetoma() throws Exception {
        UUID clave = UUID.randomUUID();
        UUID operacion = UUID.randomUUID();
        String cuerpo = lote(operacion);

        // Así queda la base si el proceso muere justo después de reservar la clave.
        reservaHuerfana(clave, cuerpo, "10 minutes");

        mockMvc.perform(enviar(clave, cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].status").value("APPLIED"));

        assertThat(formularioGuardado()).isTrue();
    }

    @Test
    @DisplayName("una reserva reciente NO se retoma: puede haber un envío en curso de verdad")
    void reservaRecienteNoSeRetoma() throws Exception {
        UUID clave = UUID.randomUUID();
        String cuerpo = lote(UUID.randomUUID());

        reservaHuerfana(clave, cuerpo, "1 second");

        mockMvc.perform(enviar(clave, cuerpo))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("idempotency_key_in_progress"));

        assertThat(formularioGuardado()).isFalse();
    }

    @Test
    @DisplayName("retomar una clave abandonada no aplica dos veces lo que ya se habia aplicado")
    void retomarNoDuplica() throws Exception {
        UUID operacion = UUID.randomUUID();
        String cuerpo = lote(operacion);

        // Primero el lote se aplica de verdad...
        mockMvc.perform(enviar(UUID.randomUUID(), cuerpo)).andExpect(status().isOk());

        // ...y después llega un reintento con una clave que quedó huérfana de un intento anterior.
        UUID clave = UUID.randomUUID();
        reservaHuerfana(clave, cuerpo, "10 minutes");

        mockMvc.perform(enviar(clave, cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].status").value("DUPLICATE"));

        Integer conFormulario = jdbcTemplate.queryForObject(
                "select count(*) from visits.visits where sync_operation_id = ?",
                Integer.class, operacion);
        assertThat(conFormulario).isEqualTo(1);
    }

    /** Deja en la base una clave reservada que nadie cerró, con la antigüedad indicada. */
    private void reservaHuerfana(UUID clave, String cuerpo, String antiguedad) {
        jdbcTemplate.update("""
                insert into sync.idempotency_keys
                    (idempotency_key, user_id, request_hash, status, created_at)
                select ?, u.id, ?, 'IN_PROGRESS', now() - interval '%s'
                  from core.users u where u.username = 'operador.demo'
                """.formatted(antiguedad), clave, huella(cuerpo));
    }

    /**
     * La misma huella que va a calcular el servidor para este cuerpo.
     *
     * <p>El servicio hashea el pedido ya deserializado y vuelto a serializar, no el texto crudo que
     * viajó. Acá se hace ese mismo recorrido con el mismo {@code ObjectMapper}, en vez de imitarlo
     * con reemplazos de texto: si las huellas no coincidieran, el test estaría probando el rechazo
     * por clave reutilizada en lugar de la recuperación.
     */
    private String huella(String cuerpo) {
        return IdempotencyService.hash(objectMapper.writeValueAsString(
                objectMapper.readValue(cuerpo, SyncBatchRequest.class)));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder enviar(
            UUID clave, String cuerpo) throws Exception {
        return post(BATCH)
                .header(HttpHeaders.AUTHORIZATION, bearerOperador())
                .header(SyncController.IDEMPOTENCY_KEY_HEADER, clave)
                .contentType(APPLICATION_JSON)
                .content(cuerpo);
    }

    private boolean formularioGuardado() {
        Integer total = jdbcTemplate.queryForObject(
                "select count(*) from visits.visits where id = ? and responses_json is not null",
                Integer.class, visita);
        return total != null && total > 0;
    }

    private String lote(UUID operacion) {
        return """
                {"operations": [
                  {"clientOperationId": "%s", "type": "VISIT_FORM", "visitId": "%s",
                   "form": {"templateKey": "mantenimiento-general", "responses":
                     {"workedHours": 5, "taskType": "PREVENTIVO", "observations": "Retomado."}}}
                ]}
                """.formatted(operacion, visita);
    }

    private String bearerOperador() throws Exception {
        String json = mockMvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content("""
                                {"username": "operador.demo", "password": "Operador123!"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(json, "$.accessToken");
    }
}
