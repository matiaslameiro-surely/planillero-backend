package ar.com.planillero.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import ar.com.planillero.AbstractIntegrationTest;
import ar.com.planillero.VisitFixtures;
import ar.com.planillero.evidence.storage.StorageProperties;
import ar.com.planillero.sync.SyncController;
import com.jayway.jsonpath.JsonPath;

/**
 * Control de acceso horizontal de los endpoints por visita (PLAN-49).
 *
 * <p>Corre contra el seed: {@code operador.demo} y {@code supervisor.demo} son de {@code ZONA_NORTE},
 * {@code admin.demo} tiene alcance global, {@code V-2001} es de {@code ZONA_SUR} y {@code V-1003} es
 * del norte y no está asignada a nadie. Son los casos del issue. Las escrituras legítimas van sobre
 * visitas que crea cada test, para no ensuciar las del seed.
 */
@SpringBootTest
@AutoConfigureMockMvc
class VisitAccessIntegrationTest extends AbstractIntegrationTest {

    /** {@code V-2001}, de la zona sur. */
    private static final UUID V_2001 = UUID.fromString("a0000002-0000-4000-8000-000000000001");
    /** {@code V-1003}, de la zona norte, sin asignar. */
    private static final UUID V_1003 = UUID.fromString("a0000001-0000-4000-8000-000000000003");

    private static final String OPERADOR = "operador.demo";

    private static final String FORMULARIO = """
            {"templateKey": "mantenimiento-general", "responses":
              {"workedHours": 1, "taskType": "CORRECTIVO", "observations": "Prueba de acceso."}}
            """;

    /** Cada endpoint que recibe un identificador de visita. */
    enum Endpoint {
        EVIDENCE_UPLOAD, EVIDENCE_LIST, EVIDENCE_FILE, MANIFEST_CREATE, MANIFEST_GET, MANIFEST_VERIFY,
        FORM_SUBMIT, FORM_GET
    }

    private static final Map<String, String> TOKENS = new HashMap<>();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StorageProperties storageProperties;

    // ------------------------------------------------------------------ rechazos

    @ParameterizedTest(name = "operador sobre una visita de otra zona: {0}")
    @EnumSource(value = Endpoint.class, names = "FORM_GET", mode = EnumSource.Mode.EXCLUDE)
    void operadorSobreVisitaDeOtraZona(Endpoint endpoint) throws Exception {
        call(endpoint, V_2001, UUID.randomUUID(), "operador.demo", "Operador123!")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("visit_not_assigned"));
    }

    @ParameterizedTest(name = "operador sobre una visita de su zona que no le asignaron: {0}")
    @EnumSource(value = Endpoint.class, names = "FORM_GET", mode = EnumSource.Mode.EXCLUDE)
    void operadorSobreVisitaNoAsignada(Endpoint endpoint) throws Exception {
        call(endpoint, V_1003, UUID.randomUUID(), "operador.demo", "Operador123!")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("visit_not_assigned"));
    }

    @ParameterizedTest(name = "supervisor sobre una visita de otra zona: {0}")
    @EnumSource(Endpoint.class)
    void supervisorSobreVisitaDeOtraZona(Endpoint endpoint) throws Exception {
        call(endpoint, V_2001, UUID.randomUUID(), "supervisor.demo", "Supervisor123!")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("outside_jurisdiction"));
    }

    @ParameterizedTest(name = "visita inexistente: {0}")
    @EnumSource(Endpoint.class)
    void visitaInexistenteResponde404(Endpoint endpoint) throws Exception {
        call(endpoint, UUID.randomUUID(), UUID.randomUUID(), "admin.demo", "Admin123!")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("visit_not_found"));
    }

    @Test
    @DisplayName("el operador tampoco lee el binario de una evidencia que existe en una visita ajena")
    void operadorNoLeeEvidenciaExistenteDeOtraZona() throws Exception {
        UUID visitaSur = VisitFixtures.createVisit(jdbcTemplate, "ZONA_SUR");
        UUID evidencia = upload(visitaSur, "admin.demo", "Admin123!");

        call(Endpoint.EVIDENCE_FILE, visitaSur, evidencia, "operador.demo", "Operador123!")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("visit_not_assigned"));
        call(Endpoint.EVIDENCE_LIST, visitaSur, null, "supervisor.demo", "Supervisor123!")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("outside_jurisdiction"));
    }

    @Test
    @DisplayName("una escritura rechazada no deja evidencia, binario, manifiesto ni formulario")
    void escrituraRechazadaNoDejaRastro() throws Exception {
        UUID visitaSur = VisitFixtures.createVisit(jdbcTemplate, "ZONA_SUR");
        UUID evidencia = upload(visitaSur, "admin.demo", "Admin123!");
        int evidenciasAntes = count("visits.evidences", visitaSur);
        int binariosAntes = storedFiles(visitaSur);

        call(Endpoint.EVIDENCE_UPLOAD, visitaSur, null, "operador.demo", "Operador123!")
                .andExpect(status().isForbidden());
        call(Endpoint.EVIDENCE_UPLOAD, visitaSur, null, "supervisor.demo", "Supervisor123!")
                .andExpect(status().isForbidden());
        call(Endpoint.MANIFEST_CREATE, visitaSur, evidencia, "operador.demo", "Operador123!")
                .andExpect(status().isForbidden());
        call(Endpoint.FORM_SUBMIT, visitaSur, null, "operador.demo", "Operador123!")
                .andExpect(status().isForbidden());

        assertThat(count("visits.evidences", visitaSur)).isEqualTo(evidenciasAntes);
        assertThat(storedFiles(visitaSur)).isEqualTo(binariosAntes);
        assertThat(count("visits.visit_manifests", visitaSur)).isZero();
        assertThat(formularioGuardado(visitaSur)).isFalse();
    }

    // ------------------------------------------------------------------ casos legítimos

    @Test
    @DisplayName("el operador opera normalmente sobre una visita de su hoja de ruta")
    void operadorSobreVisitaAsignada() throws Exception {
        UUID visita = VisitFixtures.createAssignedVisit(jdbcTemplate, "ZONA_NORTE", OPERADOR);
        fullCycle(visita, "operador.demo", "Operador123!");
    }

    @Test
    @DisplayName("el supervisor opera normalmente sobre una visita de su zona, aunque no esté asignada")
    void supervisorSobreVisitaDeSuZona() throws Exception {
        UUID visita = VisitFixtures.createVisit(jdbcTemplate, "ZONA_NORTE");
        fullCycle(visita, "supervisor.demo", "Supervisor123!");
        call(Endpoint.FORM_GET, visita, null, "supervisor.demo", "Supervisor123!")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateKey").value("mantenimiento-general"));
    }

    @Test
    @DisplayName("el administrador opera normalmente sobre una visita de cualquier zona")
    void administradorSobreCualquierVisita() throws Exception {
        UUID visita = VisitFixtures.createVisit(jdbcTemplate, "ZONA_SUR");
        fullCycle(visita, "admin.demo", "Admin123!");
        call(Endpoint.FORM_GET, visita, null, "admin.demo", "Admin123!")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jurisdiction").value("ZONA_SUR"));
    }

    // ------------------------------------------------------------------ sincronización

    @Test
    @DisplayName("sync: las operaciones sin acceso vuelven FAILED y el resto del lote se aplica")
    void syncRechazaSoloLasOperacionesSinAcceso() throws Exception {
        UUID asignada = VisitFixtures.createAssignedVisit(jdbcTemplate, "ZONA_NORTE", OPERADOR);
        UUID visitaSur = VisitFixtures.createVisit(jdbcTemplate, "ZONA_SUR");
        UUID noAsignada = VisitFixtures.createVisit(jdbcTemplate, "ZONA_NORTE");

        String cuerpo = "{\"operations\": [" + operacion(UUID.randomUUID(), asignada) + ","
                + operacion(UUID.randomUUID(), visitaSur) + ","
                + operacion(UUID.randomUUID(), noAsignada) + "]}";

        sync(cuerpo, "operador.demo", "Operador123!")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].status").value("APPLIED"))
                .andExpect(jsonPath("$.results[1].status").value("FAILED"))
                .andExpect(jsonPath("$.results[1].error").value("visit_not_assigned"))
                .andExpect(jsonPath("$.results[2].status").value("FAILED"))
                .andExpect(jsonPath("$.results[2].error").value("visit_not_assigned"));

        assertThat(formularioGuardado(asignada)).isTrue();
        assertThat(formularioGuardado(visitaSur)).isFalse();
        assertThat(formularioGuardado(noAsignada)).isFalse();
    }

    @Test
    @DisplayName("sync: el supervisor no carga formularios de otra zona, sí de la suya")
    void syncSupervisorFueraDeSuZona() throws Exception {
        UUID visitaNorte = VisitFixtures.createVisit(jdbcTemplate, "ZONA_NORTE");
        UUID visitaSur = VisitFixtures.createVisit(jdbcTemplate, "ZONA_SUR");

        String cuerpo = "{\"operations\": [" + operacion(UUID.randomUUID(), visitaNorte) + ","
                + operacion(UUID.randomUUID(), visitaSur) + "]}";

        sync(cuerpo, "supervisor.demo", "Supervisor123!")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].status").value("APPLIED"))
                .andExpect(jsonPath("$.results[1].status").value("FAILED"))
                .andExpect(jsonPath("$.results[1].error").value("outside_jurisdiction"));

        assertThat(formularioGuardado(visitaSur)).isFalse();
    }

    @Test
    @DisplayName("sync: repetir la operación de otro operador no la devuelve como DUPLICATE")
    void syncNoDevuelveLaOperacionDeOtroOperador() throws Exception {
        UUID visita = VisitFixtures.createAssignedVisit(jdbcTemplate, "ZONA_NORTE", OPERADOR);
        UUID operacionId = UUID.randomUUID();
        String cuerpo = "{\"operations\": [" + operacion(operacionId, visita) + "]}";

        sync(cuerpo, "operador.demo", "Operador123!")
                .andExpect(jsonPath("$.results[0].status").value("APPLIED"));

        // Otro operador de la misma zona reenvía la misma operación: sin el control previo, el camino
        // rápido de repetidas le devolvería la confirmación del formulario ajeno.
        sync(cuerpo, "operador.norte2", "Operador123!")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].status").value("FAILED"))
                .andExpect(jsonPath("$.results[0].error").value("visit_not_assigned"));

        // El dueño, en cambio, recibe la respuesta de siempre.
        sync(cuerpo, "operador.demo", "Operador123!")
                .andExpect(jsonPath("$.results[0].status").value("DUPLICATE"));
    }

    // ------------------------------------------------------------------ helpers

    /** Sube dos evidencias, sella el manifiesto, lo consulta y lo verifica, y carga el formulario. */
    private void fullCycle(UUID visita, String username, String password) throws Exception {
        UUID evidencia = upload(visita, username, password);

        call(Endpoint.EVIDENCE_LIST, visita, null, username, password)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        call(Endpoint.EVIDENCE_FILE, visita, evidencia, username, password)
                .andExpect(status().isOk());
        call(Endpoint.MANIFEST_CREATE, visita, evidencia, username, password)
                .andExpect(status().isCreated());
        call(Endpoint.MANIFEST_GET, visita, null, username, password)
                .andExpect(status().isOk());
        call(Endpoint.MANIFEST_VERIFY, visita, null, username, password)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"));
        call(Endpoint.FORM_SUBMIT, visita, null, username, password)
                .andExpect(status().isOk());

        assertThat(formularioGuardado(visita)).isTrue();
    }

    private UUID upload(UUID visita, String username, String password) throws Exception {
        String json = call(Endpoint.EVIDENCE_UPLOAD, visita, null, username, password)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(json, "$.id"));
    }

    private ResultActions call(Endpoint endpoint, UUID visita, UUID evidencia, String username,
            String password) throws Exception {
        String bearer = bearer(username, password);
        return switch (endpoint) {
            case EVIDENCE_UPLOAD -> mockMvc.perform(multipart("/api/v1/visits/{visitId}/evidences", visita)
                    .file(new MockMultipartFile("file", "fachada.jpg", "image/jpeg",
                            ("foto " + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8)))
                    .param("type", "PHOTO")
                    .header(HttpHeaders.AUTHORIZATION, bearer));
            case EVIDENCE_LIST -> mockMvc.perform(get("/api/v1/visits/{visitId}/evidences", visita)
                    .header(HttpHeaders.AUTHORIZATION, bearer));
            case EVIDENCE_FILE -> mockMvc.perform(
                    get("/api/v1/visits/{visitId}/evidences/{evidenceId}/file", visita, evidencia)
                            .header(HttpHeaders.AUTHORIZATION, bearer));
            case MANIFEST_CREATE -> mockMvc.perform(post("/api/v1/visits/{visitId}/manifest", visita)
                    .contentType(APPLICATION_JSON)
                    .content("{\"deviceInfo\": \"Dispositivo de prueba\", \"evidenceIds\": [\"" + evidencia + "\"]}")
                    .header(HttpHeaders.AUTHORIZATION, bearer));
            case MANIFEST_GET -> mockMvc.perform(get("/api/v1/visits/{visitId}/manifest", visita)
                    .header(HttpHeaders.AUTHORIZATION, bearer));
            case MANIFEST_VERIFY -> mockMvc.perform(post("/api/v1/visits/{visitId}/manifest/verify", visita)
                    .header(HttpHeaders.AUTHORIZATION, bearer));
            case FORM_SUBMIT -> mockMvc.perform(post("/api/v1/visitas/{id}/formulario", visita)
                    .contentType(APPLICATION_JSON)
                    .content(FORMULARIO)
                    .header(HttpHeaders.AUTHORIZATION, bearer));
            case FORM_GET -> mockMvc.perform(get("/api/v1/visitas/{id}/formulario", visita)
                    .header(HttpHeaders.AUTHORIZATION, bearer));
        };
    }

    private ResultActions sync(String cuerpo, String username, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/sync/batch")
                .header(HttpHeaders.AUTHORIZATION, bearer(username, password))
                .header(SyncController.IDEMPOTENCY_KEY_HEADER, UUID.randomUUID())
                .contentType(APPLICATION_JSON)
                .content(cuerpo));
    }

    private static String operacion(UUID operacionId, UUID visita) {
        return """
                {"clientOperationId": "%s", "type": "VISIT_FORM", "visitId": "%s", "form": %s}
                """.formatted(operacionId, visita, FORMULARIO);
    }

    private int count(String table, UUID visita) {
        Integer total = jdbcTemplate.queryForObject(
                "select count(*) from " + table + " where visit_id = ?", Integer.class, visita);
        return total == null ? 0 : total;
    }

    private boolean formularioGuardado(UUID visita) {
        Integer total = jdbcTemplate.queryForObject(
                "select count(*) from visits.visits where id = ? and responses_json is not null",
                Integer.class, visita);
        return total != null && total > 0;
    }

    /** Binarios guardados en el storage local para la visita (la clave es {@code visits/<id>/…}). */
    private int storedFiles(UUID visita) {
        File[] files = new File(storageProperties.getLocalDir(), "visits/" + visita).listFiles();
        return files == null ? 0 : files.length;
    }

    private String bearer(String username, String password) throws Exception {
        String token = TOKENS.get(username);
        if (token == null) {
            String json = mockMvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                            .content("{\"username\": \"" + username + "\", \"password\": \"" + password + "\"}"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            token = JsonPath.read(json, "$.accessToken");
            TOKENS.put(username, token);
        }
        return "Bearer " + token;
    }
}
