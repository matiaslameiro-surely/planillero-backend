package ar.com.planillero.forms;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import ar.com.planillero.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;

/**
 * Pruebas del catálogo de plantillas contra la aplicación completa.
 *
 * <p>Corren con la cadena de seguridad real y con las plantillas de seed de {@code V5}, así que
 * validan también que el JSONB de la base vuelva al cliente como JSON y no como texto escapado.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FormTemplateIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/v1/plantillas sin token responde 401")
    void listarSinTokenResponde401() throws Exception {
        mockMvc.perform(get("/api/v1/plantillas"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/plantillas devuelve sólo las plantillas vigentes, con su schema")
    void listarDevuelveSoloLasVigentes() throws Exception {
        mockMvc.perform(get("/api/v1/plantillas").header(HttpHeaders.AUTHORIZATION, bearerOperador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                // La v1 de mantenimiento-general está inactiva: no tiene que aparecer.
                .andExpect(jsonPath("$[?(@.key == 'mantenimiento-general')].version")
                        .value(Matchers.contains(2)))
                .andExpect(jsonPath("$[?(@.key == 'control-de-acceso')].version")
                        .value(Matchers.contains(1)))
                // El schema viaja como objeto JSON, listo para que el cliente arme los campos.
                .andExpect(jsonPath("$[0].schema.type").value("object"));
    }

    @Test
    @DisplayName("GET /api/v1/plantillas/{clave} devuelve la última versión vigente")
    void porClaveDevuelveLaUltimaVersionVigente() throws Exception {
        mockMvc.perform(get("/api/v1/plantillas/mantenimiento-general")
                        .header(HttpHeaders.AUTHORIZATION, bearerOperador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("mantenimiento-general"))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.schema.properties.serialNumber.pattern").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/plantillas/{clave} de una clave inexistente responde 404")
    void claveInexistenteResponde404() throws Exception {
        mockMvc.perform(get("/api/v1/plantillas/no-existe")
                        .header(HttpHeaders.AUTHORIZATION, bearerOperador()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("template_not_found"));
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
