package ar.com.planillero.supervision;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import ar.com.planillero.AbstractIntegrationTest;
import ar.com.planillero.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Pruebas de integración del Tablero Central de Supervisión y telemetría de operadores.
 */
@AutoConfigureMockMvc
class SupervisionIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OperatorShiftRepository operatorShiftRepository;

    @Autowired
    private UserRepository userRepository;

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
    @DisplayName("POST /heartbeat con networkStatus fuera de ONLINE, OFFLINE o UNKNOWN: 400 sin tocar el turno")
    void latidoConNetworkStatusInvalidoResponde400() throws Exception {
        String token = operatorToken();
        sendHeartbeat(token, "{\"networkStatus\":\"OFFLINE\"}").andExpect(status().isOk());
        OperatorShift before = todayShift();

        for (String invalid : new String[] {"WIFI", "online", ""}) {
            sendHeartbeat(token, "{\"batteryLevel\":0.8,\"networkStatus\":\"" + invalid + "\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("invalid_request"))
                    .andExpect(jsonPath("$.message").value(startsWith("networkStatus:")));
        }

        // El pedido se rechaza antes del servicio: ni el estado de red ni el último latido cambian.
        OperatorShift after = todayShift();
        assertEquals("OFFLINE", after.getNetworkStatus());
        assertEquals(before.getLastHeartbeatAt(), after.getLastHeartbeatAt());
    }

    @Test
    @DisplayName("POST /heartbeat guarda ONLINE, OFFLINE y UNKNOWN; sin networkStatus conserva el valor anterior")
    void latidoGuardaNetworkStatusValidos() throws Exception {
        String token = operatorToken();

        for (String valid : new String[] {"ONLINE", "OFFLINE", "UNKNOWN"}) {
            sendHeartbeat(token, "{\"networkStatus\":\"" + valid + "\"}").andExpect(status().isOk());
            assertEquals(valid, todayShift().getNetworkStatus());
        }

        sendHeartbeat(token, "{\"networkStatus\":\"OFFLINE\"}").andExpect(status().isOk());
        sendHeartbeat(token, "{\"batteryLevel\":0.6}").andExpect(status().isOk());
        assertEquals("OFFLINE", todayShift().getNetworkStatus());
        sendHeartbeat(token, "{\"networkStatus\":null}").andExpect(status().isOk());
        assertEquals("OFFLINE", todayShift().getNetworkStatus());
    }

    @Test
    @DisplayName("POST /heartbeat guarda y GET /operadores/estado devuelve observaciones tal cual con acentos, comillas y etiquetas HTML")
    void latidoGuardaYDevuelveObservacionesSinEscapeHtml() throws Exception {
        String opToken = operatorToken();
        String supToken = supervisorToken();
        String rawObservation = "Demora por tránsito en Ñuñoa, \"calle cortada\" <script>alert(1)</script>";

        String payload = """
                {
                    "batteryLevel": 0.90,
                    "networkStatus": "ONLINE",
                    "observations": "%s"
                }
                """.formatted(rawObservation.replace("\"", "\\\""));

        sendHeartbeat(opToken, payload).andExpect(status().isOk());

        // Verificar que en base de datos quedó en texto plano
        assertEquals(rawObservation, todayShift().getObservations());

        // Verificar que GET /operadores/estado lo devuelve idéntico sin entidades HTML
        mockMvc.perform(get("/api/v1/supervision/operadores/estado")
                        .header("Authorization", "Bearer " + supToken)
                        .param("date", LocalDate.now().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.username == 'operador.demo')].observations")
                        .value(hasItem(rawObservation)));
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

    private ResultActions sendHeartbeat(String token, String payload)
            throws Exception {
        return mockMvc.perform(post("/api/v1/supervision/heartbeat")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(payload));
    }

    /** El turno de hoy de {@code operador.demo}, leído de la base. */
    private OperatorShift todayShift() {
        UUID operatorId = userRepository.findByUsername("operador.demo").orElseThrow().getId();
        return operatorShiftRepository.findByOperatorIdAndShiftDate(operatorId, LocalDate.now()).orElseThrow();
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
