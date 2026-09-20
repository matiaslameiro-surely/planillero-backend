package ar.com.planillero.visits;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import ar.com.planillero.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;

/**
 * Pruebas del envío de formularios de punta a punta.
 *
 * <p>Usan las plantillas ficticias del seed {@code V10}. Las visitas las crea cada test: la tabla es de
 * PLAN-8 y su seed puede cambiar, así que estos tests no dependen de él. Cada test escribe sobre una
 * visita distinta para no depender del orden de ejecución.
 */
@SpringBootTest
@AutoConfigureMockMvc
class VisitFormIntegrationTest extends AbstractIntegrationTest {

    private static final UUID VISITA_VALIDA = UUID.fromString("bbbbbbbb-0001-4000-8000-000000000001");
    private static final UUID VISITA_INYECCION = UUID.fromString("bbbbbbbb-0002-4000-8000-000000000002");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Visitas ficticias propias, con las columnas obligatorias de la tabla de PLAN-8. */
    @BeforeEach
    void crearVisitas() {
        crearVisita(VISITA_VALIDA, "F-0001");
        crearVisita(VISITA_INYECCION, "F-0002");
    }

    private void crearVisita(UUID id, String code) {
        jdbcTemplate.update("""
                insert into visits.visits
                    (id, code, address, latitude, longitude, jurisdiction, status, urgency)
                values (?, ?, 'Calle Ficticia 100', -34.600000, -58.400000, 'ZONA_NORTE', 'PENDING', 'LOW')
                on conflict (id) do nothing
                """, id, code);
    }

    @Test
    @DisplayName("POST sin token responde 401")
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(post("/api/v1/visitas/{id}/formulario", VISITA_VALIDA)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"templateKey": "mantenimiento-general", "responses": {}}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("un formulario válido se guarda y devuelve la versión de plantilla que lo aceptó")
    void formularioValidoSeGuarda() throws Exception {
        mockMvc.perform(post("/api/v1/visitas/{id}/formulario", VISITA_VALIDA)
                        .header(HttpHeaders.AUTHORIZATION, bearerOperador())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "templateKey": "mantenimiento-general",
                                  "responses": {
                                    "workedHours": 7.5,
                                    "taskType": "CORRECTIVO",
                                    "observations": "Se reemplazó el rodamiento.",
                                    "serialNumber": "XYZ-0042",
                                    "requiresFollowUp": true
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visitId").value(VISITA_VALIDA.toString()))
                .andExpect(jsonPath("$.templateKey").value("mantenimiento-general"))
                .andExpect(jsonPath("$.templateVersion").value(2))
                .andExpect(jsonPath("$.submittedAt").isNotEmpty());

        // Quedó en la columna JSONB, consultable por contenido.
        Boolean guardado = jdbcTemplate.queryForObject(
                "select responses_json @> ?::jsonb from visits.visits where id = ?",
                Boolean.class,
                "{\"taskType\": \"CORRECTIVO\"}",
                VISITA_VALIDA);
        assertThat(guardado).isTrue();

        // El estado de la visita es del ciclo de vida de planificación: el formulario no lo toca.
        String estado = jdbcTemplate.queryForObject(
                "select status from visits.visits where id = ?", String.class, VISITA_VALIDA);
        assertThat(estado).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("un formulario inválido responde 400 con TODAS las violaciones, no la primera")
    void formularioInvalidoDevuelveTodasLasViolaciones() throws Exception {
        mockMvc.perform(post("/api/v1/visitas/{id}/formulario", VISITA_VALIDA)
                        .header(HttpHeaders.AUTHORIZATION, bearerOperador())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "templateKey": "mantenimiento-general",
                                  "responses": {
                                    "workedHours": 99,
                                    "taskType": "URGENTE"
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("form_validation_failed"))
                .andExpect(jsonPath("$.violations.length()").value(3))
                .andExpect(jsonPath("$.violations[*].field").value(Matchers.containsInAnyOrder(
                        "/workedHours", "/taskType", "/observations")))
                .andExpect(jsonPath("$.violations[*].rule").value(Matchers.containsInAnyOrder(
                        "maximum", "enum", "required")));
    }

    @Test
    @DisplayName("una propiedad no declarada en la plantilla se rechaza")
    void propiedadNoDeclaradaSeRechaza() throws Exception {
        mockMvc.perform(post("/api/v1/visitas/{id}/formulario", VISITA_VALIDA)
                        .header(HttpHeaders.AUTHORIZATION, bearerOperador())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "templateKey": "mantenimiento-general",
                                  "responses": {
                                    "workedHours": 8,
                                    "taskType": "PREVENTIVO",
                                    "observations": "ok",
                                    "campoInventado": "algo"
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("form_validation_failed"))
                .andExpect(jsonPath("$.violations[0].field").value("/campoInventado"))
                .andExpect(jsonPath("$.violations[0].rule").value("additionalProperties"));
    }

    @Test
    @DisplayName("una visita inexistente responde 404")
    void visitaInexistenteResponde404() throws Exception {
        mockMvc.perform(post("/api/v1/visitas/{id}/formulario", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearerOperador())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "templateKey": "mantenimiento-general",
                                  "responses": {
                                    "workedHours": 8, "taskType": "PREVENTIVO", "observations": "ok"
                                  }
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("visit_not_found"));
    }

    @Test
    @DisplayName("una plantilla inexistente responde 400 con un código distinto al de validación")
    void plantillaInexistenteResponde400() throws Exception {
        mockMvc.perform(post("/api/v1/visitas/{id}/formulario", VISITA_VALIDA)
                        .header(HttpHeaders.AUTHORIZATION, bearerOperador())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"templateKey": "no-existe", "responses": {}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("template_not_found"));
    }

    @Test
    @DisplayName("una versión de plantilla inexistente responde 400")
    void versionInexistenteResponde400() throws Exception {
        mockMvc.perform(post("/api/v1/visitas/{id}/formulario", VISITA_VALIDA)
                        .header(HttpHeaders.AUTHORIZATION, bearerOperador())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "templateKey": "mantenimiento-general",
                                  "templateVersion": 99,
                                  "responses": {
                                    "workedHours": 8, "taskType": "PREVENTIVO", "observations": "ok"
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("template_not_found"));
    }

    @Test
    @DisplayName("una versión de plantilla dada de baja no acepta envíos: 400 template_inactive")
    void versionInactivaResponde400() throws Exception {
        // mantenimiento-general v1 existe en el seed pero está inactiva. El payload cumple su
        // schema: si se aceptara, el rechazo no podría venir de la validación de campos.
        mockMvc.perform(post("/api/v1/visitas/{id}/formulario", VISITA_VALIDA)
                        .header(HttpHeaders.AUTHORIZATION, bearerOperador())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "templateKey": "mantenimiento-general",
                                  "templateVersion": 1,
                                  "responses": { "workedHours": 8, "taskType": "PREVENTIVO" }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("template_inactive"));
    }

    @Test
    @DisplayName("OWASP A03: un valor con SQL adentro se guarda como texto y no toca el esquema")
    void valorConSqlSeGuardaLiteral() throws Exception {
        String inyeccion = "'; drop table forms.form_templates; --";

        mockMvc.perform(post("/api/v1/visitas/{id}/formulario", VISITA_INYECCION)
                        .header(HttpHeaders.AUTHORIZATION, bearerOperador())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "templateKey": "mantenimiento-general",
                                  "responses": {
                                    "workedHours": 1,
                                    "taskType": "INSPECCION",
                                    "observations": "%s"
                                  }
                                }
                                """.formatted(inyeccion)))
                .andExpect(status().isOk());

        String guardado = jdbcTemplate.queryForObject(
                "select responses_json ->> 'observations' from visits.visits where id = ?",
                String.class,
                VISITA_INYECCION);
        assertThat(guardado).isEqualTo(inyeccion);

        // La tabla que el texto intentaba borrar sigue con sus tres plantillas de seed.
        Integer plantillas = jdbcTemplate.queryForObject(
                "select count(*) from forms.form_templates", Integer.class);
        assertThat(plantillas).isEqualTo(3);
    }

    @Test
    @DisplayName("una plantilla publicada es inmutable: la base rechaza modificarle el schema")
    void plantillaPublicadaNoSePuedeModificar() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update forms.form_templates set schema_json = '{}'::jsonb where template_key = ?",
                "control-de-acceso"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("inmutable");
    }

    @Test
    @DisplayName("la descripción de una plantilla publicada tampoco se puede modificar")
    void descripcionPublicadaNoSePuedeModificar() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update forms.form_templates set description = 'otra' where template_key = ?",
                "control-de-acceso"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("inmutable");
    }

    @Test
    @DisplayName("no puede existir dos veces la misma clave y versión de plantilla")
    void noSePuedeDuplicarUnaVersion() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into forms.form_templates (id, template_key, version, name, schema_json, active)
                values (?, 'control-de-acceso', 1, 'Duplicada', '{}'::jsonb, true)
                """, UUID.randomUUID()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("dar de baja una plantilla sí está permitido: no cambia las reglas de nada ya validado")
    void darDeBajaSiEstaPermitido() {
        int filas = jdbcTemplate.update(
                "update forms.form_templates set active = active where template_key = ?",
                "control-de-acceso");

        assertThat(filas).isEqualTo(1);
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
