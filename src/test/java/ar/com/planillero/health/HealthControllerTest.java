package ar.com.planillero.health;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pruebas unitarias para {@link HealthController}.
 *
 * <p>Verifica el contrato JSON, las rutas canónicas y de compatibilidad, y la propagación del
 * estado operativo hacia la respuesta HTTP.
 *
 * <p>Los filtros de seguridad se apagan porque acá se prueba el controller, no el acceso: que
 * {@code /health} y {@code /salud} sean públicos se verifica en {@code AuthIntegrationTest}.
 */
@WebMvcTest(HealthController.class)
@AutoConfigureMockMvc(addFilters = false)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DatabaseHealthService databaseHealthService;

    @Test
    @DisplayName("GET /health responde 200 con estado UP y métricas de base de datos")
    void shouldReturnHealthUp() throws Exception {
        given(databaseHealthService.checkHealth())
                .willReturn(new DatabaseHealthResponse("UP", 8L));

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.database.status").value("UP"))
                .andExpect(jsonPath("$.database.latencyMs").value(8))
                .andExpect(jsonPath("$.estado").value("ok"))
                .andExpect(jsonPath("$.momento").isNotEmpty());
    }

    @Test
    @DisplayName("GET /salud responde 200 por retrocompatibilidad con clientes existentes")
    void shouldReturnHealthOnLegacyEndpoint() throws Exception {
        given(databaseHealthService.checkHealth())
                .willReturn(new DatabaseHealthResponse("UP", 5L));

        mockMvc.perform(get("/salud"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database.status").value("UP"))
                .andExpect(jsonPath("$.estado").value("ok"))
                .andExpect(jsonPath("$.momento").isNotEmpty());
    }

    @Test
    @DisplayName("GET /health reporta DEGRADED si la base de datos no está disponible")
    void shouldReturnDegradedWhenDatabaseIsDown() throws Exception {
        given(databaseHealthService.checkHealth())
                .willReturn(new DatabaseHealthResponse("DOWN", 12L));

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEGRADED"))
                .andExpect(jsonPath("$.database.status").value("DOWN"))
                .andExpect(jsonPath("$.database.latencyMs").value(12))
                .andExpect(jsonPath("$.estado").value("error"))
                .andExpect(jsonPath("$.momento").isNotEmpty());
    }
}
