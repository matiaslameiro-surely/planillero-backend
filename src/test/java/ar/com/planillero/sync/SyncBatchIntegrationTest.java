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
import com.jayway.jsonpath.JsonPath;

/**
 * Sincronización por lote de punta a punta.
 *
 * <p>Usa las plantillas ficticias del seed {@code V10} y visitas propias, creadas por cada test: la
 * tabla de visitas es de PLAN-8 y su seed puede cambiar.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SyncBatchIntegrationTest extends AbstractIntegrationTest {

    private static final String BATCH = "/api/v1/sync/batch";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID visita;

    @BeforeEach
    void crearVisita() {
        visita = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into visits.visits
                    (id, code, address, latitude, longitude, jurisdiction, status, urgency)
                values (?, ?, 'Calle Ficticia 200', -34.600000, -58.400000, 'ZONA_NORTE', 'PENDING', 'LOW')
                """, visita, "S-" + visita.toString().substring(0, 8));
    }

    @Test
    @DisplayName("sin token responde 401")
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(post(BATCH)
                        .header(SyncController.IDEMPOTENCY_KEY_HEADER, UUID.randomUUID())
                        .contentType(APPLICATION_JSON)
                        .content(lote(UUID.randomUUID(), visita)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("sin el header Idempotency-Key responde 400 y no escribe nada")
    void sinHeaderResponde400() throws Exception {
        mockMvc.perform(post(BATCH)
                        .header(HttpHeaders.AUTHORIZATION, bearerOperador())
                        .contentType(APPLICATION_JSON)
                        .content(lote(UUID.randomUUID(), visita)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("idempotency_key_required"));

        assertThat(formulariosGuardados()).isZero();
    }

    @Test
    @DisplayName("con una clave que no es UUID v4 responde 400")
    void claveInvalidaResponde400() throws Exception {
        // UUID válido pero de versión 1: sintácticamente correcto y aun así inaceptable, porque su
        // unicidad depende del reloj y la MAC, no del azar.
        for (String clave : new String[] {"lote-de-hoy", "1", "00000000-0000-1000-8000-000000000001"}) {
            mockMvc.perform(post(BATCH)
                            .header(HttpHeaders.AUTHORIZATION, bearerOperador())
                            .header(SyncController.IDEMPOTENCY_KEY_HEADER, clave)
                            .contentType(APPLICATION_JSON)
                            .content(lote(UUID.randomUUID(), visita)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("idempotency_key_invalid"));
        }

        assertThat(formulariosGuardados()).isZero();
    }

    @Test
    @DisplayName("un lote válido aplica el formulario y lo marca como sincronizado en diferido")
    void loteValidoSeAplica() throws Exception {
        UUID operacion = UUID.randomUUID();

        mockMvc.perform(enviar(UUID.randomUUID(), lote(operacion, visita)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].clientOperationId").value(operacion.toString()))
                .andExpect(jsonPath("$.results[0].status").value("APPLIED"))
                .andExpect(jsonPath("$.results[0].form.templateKey").value("mantenimiento-general"))
                .andExpect(jsonPath("$.results[0].form.templateVersion").value(2));

        assertThat(formulariosGuardados()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select synced_deferred from visits.visits where id = ?", Boolean.class, visita))
                .isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "select sync_operation_id from visits.visits where id = ?", UUID.class, visita))
                .isEqualTo(operacion);
    }

    @Test
    @DisplayName("reenviar el mismo lote con la misma clave devuelve la respuesta original sin reescribir")
    void reenvioIdenticoDevuelveLoGuardado() throws Exception {
        UUID clave = UUID.randomUUID();
        UUID operacion = UUID.randomUUID();
        String cuerpo = lote(operacion, visita);

        String primera = mockMvc.perform(enviar(clave, cuerpo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String segunda = mockMvc.perform(enviar(clave, cuerpo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Byte por byte: incluso el `submittedAt` es el del primer envío. Si se reprocesara, la hora
        // sería otra y el cliente no podría saber cuál de las dos respuestas es la buena.
        assertThat(segunda).isEqualTo(primera);
        assertThat(formulariosGuardados()).isEqualTo(1);
    }

    @Test
    @DisplayName("la misma operación en otro lote se resuelve como duplicada, no se aplica de nuevo")
    void mismaOperacionEnOtroLoteEsDuplicada() throws Exception {
        UUID operacion = UUID.randomUUID();

        mockMvc.perform(enviar(UUID.randomUUID(), lote(operacion, visita)))
                .andExpect(jsonPath("$.results[0].status").value("APPLIED"));

        // Otro lote, otra clave, la misma operación: es el caso del cliente que recorta o reagrupa su
        // cola. La clave de lote no lo cubre; el identificador de operación sí.
        mockMvc.perform(enviar(UUID.randomUUID(), lote(operacion, visita)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].status").value("DUPLICATE"))
                .andExpect(jsonPath("$.results[0].form.templateKey").value("mantenimiento-general"));

        assertThat(formulariosGuardados()).isEqualTo(1);
    }

    @Test
    @DisplayName("la misma clave con otro cuerpo responde 409 y no aplica nada")
    void claveReusadaConOtroCuerpoResponde409() throws Exception {
        UUID clave = UUID.randomUUID();

        mockMvc.perform(enviar(clave, lote(UUID.randomUUID(), visita)))
                .andExpect(status().isOk());

        UUID otraOperacion = UUID.randomUUID();
        mockMvc.perform(enviar(clave, lote(otraOperacion, visita)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("idempotency_key_reused"));

        assertThat(formulariosGuardados()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from visits.visits where sync_operation_id = ?",
                Integer.class, otraOperacion))
                .isZero();
    }

    @Test
    @DisplayName("una operación inválida no aborta el lote: las demás se aplican igual")
    void operacionInvalidaNoAbortaElLote() throws Exception {
        UUID buena = UUID.randomUUID();
        UUID visitaInexistente = UUID.randomUUID();
        UUID sinVisita = UUID.randomUUID();
        UUID formularioInvalido = UUID.randomUUID();

        String cuerpo = """
                {"operations": [
                  %s,
                  {"clientOperationId": "%s", "type": "VISIT_FORM", "visitId": "%s",
                   "form": {"templateKey": "mantenimiento-general", "responses":
                     {"workedHours": 8, "taskType": "PREVENTIVO", "observations": "ok"}}},
                  {"clientOperationId": "%s", "type": "VISIT_FORM", "visitId": "%s",
                   "form": {"templateKey": "mantenimiento-general", "responses":
                     {"workedHours": 99, "taskType": "URGENTE"}}}
                ]}
                """.formatted(operacion(buena, visita), sinVisita, visitaInexistente,
                formularioInvalido, visita);

        mockMvc.perform(enviar(UUID.randomUUID(), cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(3))
                .andExpect(jsonPath("$.results[0].status").value("APPLIED"))
                .andExpect(jsonPath("$.results[1].status").value("FAILED"))
                .andExpect(jsonPath("$.results[1].error").value("visit_not_found"))
                .andExpect(jsonPath("$.results[2].status").value("FAILED"))
                .andExpect(jsonPath("$.results[2].error").value("form_validation_failed"));

        // La buena quedó guardada pese a que dos de sus compañeras de lote fallaron.
        assertThat(formulariosGuardados()).isEqualTo(1);
    }

    @Test
    @DisplayName("un lote vacío responde 400")
    void loteVacioResponde400() throws Exception {
        mockMvc.perform(enviar(UUID.randomUUID(), """
                {"operations": []}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder enviar(
            UUID clave, String cuerpo) throws Exception {
        return post(BATCH)
                .header(HttpHeaders.AUTHORIZATION, bearerOperador())
                .header(SyncController.IDEMPOTENCY_KEY_HEADER, clave)
                .contentType(APPLICATION_JSON)
                .content(cuerpo);
    }

    private int formulariosGuardados() {
        Integer total = jdbcTemplate.queryForObject(
                "select count(*) from visits.visits where id = ? and responses_json is not null",
                Integer.class, visita);
        return total == null ? 0 : total;
    }

    private static String lote(UUID operacion, UUID visitId) {
        return "{\"operations\": [" + operacion(operacion, visitId) + "]}";
    }

    private static String operacion(UUID operacion, UUID visitId) {
        return """
                {"clientOperationId": "%s", "type": "VISIT_FORM", "visitId": "%s",
                 "form": {"templateKey": "mantenimiento-general", "responses":
                   {"workedHours": 6.5, "taskType": "PREVENTIVO", "observations": "Sin novedades."}}}
                """.formatted(operacion, visitId);
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
