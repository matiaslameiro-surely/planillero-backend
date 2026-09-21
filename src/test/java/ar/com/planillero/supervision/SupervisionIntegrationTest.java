package ar.com.planillero.supervision;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import ar.com.planillero.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pruebas de integración del Tablero Central de Supervisión y telemetría de operadores.
 */
@AutoConfigureMockMvc
class SupervisionIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("los endpoints de supervisión exigen token: 401 sin autenticación")
    void endpointsSinTokenResponden401() throws Exception {
        mockMvc.perform(get("/api/v1/supervision/tablero-resumen"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/supervision/operadores/estado"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/supervision/heartbeat").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("un operador no puede consultar el tablero de supervisión: 403 forbidden")
    void operadorNoConsultaSupervision() throws Exception {
        String token = operatorToken();
        mockMvc.perform(get("/api/v1/supervision/tablero-resumen").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/supervision/operadores/estado").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("un supervisor no puede emitir latidos: 403 forbidden")
    void supervisorNoEmiteLatido() throws Exception {
        String token = supervisorToken();
        mockMvc.perform(post("/api/v1/supervision/heartbeat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"batteryLevel\":0.85,\"networkStatus\":\"ONLINE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /tablero-resumen devuelve KPIs y excepciones acotados a la jurisdicción (OWASP A01)")
    void resumenAcotadoAJurisdiccion() throws Exception {
        String token = supervisorToken(); // ZONA_NORTE
        mockMvc.perform(get("/api/v1/supervision/tablero-resumen")
                        .header("Authorization", "Bearer " + token)
                        .param("date", LocalDate.now().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jurisdiction").value("ZONA_NORTE"))
                .andExpect(jsonPath("$.totalOperators").value(2))
                .andExpect(jsonPath("$.totalVisits").value(greaterThanOrEqualTo(6)))
                .andExpect(jsonPath("$.slaComplianceRate").isNumber());
    }

    @Test
    @DisplayName("GET /operadores/estado devuelve los operadores de la zona con telemetría")
    void operadoresEstadoZonaNorte() throws Exception {
        String token = supervisorToken(); // ZONA_NORTE
        mockMvc.perform(get("/api/v1/supervision/operadores/estado")
                        .header("Authorization", "Bearer " + token)
                        .param("date", LocalDate.now().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].jurisdiction").value("ZONA_NORTE"))
                .andExpect(jsonPath("$[1].jurisdiction").value("ZONA_NORTE"))
                // No debe contener al operador de ZONA_SUR
                .andExpect(jsonPath("$[?(@.username == 'operador.sur')]").isEmpty());
    }

    @Test
    @DisplayName("POST /heartbeat registra latido periódico de operador y actualiza telemetría")
    void operadorRegistraLatido() throws Exception {
        String token = operatorToken();
        String payload = """
                {
                    "batteryLevel": 0.75,
                    "networkStatus": "ONLINE",
                    "latitude": -34.521122,
                    "longitude": -58.479988,
                    "observations": "Recorrido sin incidentes"
                }
                """;

        mockMvc.perform(post("/api/v1/supervision/heartbeat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").isNotEmpty())
                .andExpect(jsonPath("$.serverTimestamp").isNotEmpty());
    }

    @Test
    @DisplayName("Prueba de carga y rendimiento de endpoints analíticos de supervisión")
    void pruebaDeCargaEndpointsSupervision() throws Exception {
        String token = supervisorToken();
        long start = System.currentTimeMillis();

        for (int i = 0; i < 15; i++) {
            mockMvc.perform(get("/api/v1/supervision/tablero-resumen")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/supervision/operadores/estado")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }

        long duration = System.currentTimeMillis() - start;
        // 30 peticiones analíticas deben responder holgadamente en menos de 5 segundos
        org.junit.jupiter.api.Assertions.assertTrue(duration < 5000,
                "Los endpoints analíticos tardaron " + duration + "ms, superando el umbral de performance");
    }

    private String supervisorToken() throws Exception {
        return accessToken("supervisor.demo", "Supervisor123!");
    }

    private String operatorToken() throws Exception {
        return accessToken("operador.demo", "Operador123!");
    }

    private String accessToken(String username, String password) throws Exception {
        String json = mockMvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.accessToken");
    }
}
