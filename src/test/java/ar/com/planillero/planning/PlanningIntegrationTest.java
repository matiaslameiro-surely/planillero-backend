package ar.com.planillero.planning;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.com.planillero.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pruebas de integración de la planificación de rutas.
 *
 * <p>Corren contra un PostgreSQL real (Testcontainers) con la cadena de seguridad completa. Cada
 * prueba se arma su propio estado vía la API (con fechas distintas) para no depender del orden de
 * ejecución, y valida de punta a punta el orden por urgencia, la reasignación, el acceso horizontal
 * por jurisdicción (OWASP A01) y los errores 400/404.
 */
@AutoConfigureMockMvc
class PlanningIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private static final UUID OPERADOR_DEMO = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID OPERADOR_NORTE2 = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID OPERADOR_SUR = UUID.fromString("55555555-5555-4555-8555-555555555555");

    private static final UUID VISITA_NORTE_HIGH = UUID.fromString("a0000001-0000-4000-8000-000000000001");
    private static final UUID VISITA_NORTE_MEDIA = UUID.fromString("a0000001-0000-4000-8000-000000000002");
    private static final UUID VISITA_NORTE_BAJA = UUID.fromString("a0000001-0000-4000-8000-000000000004");
    private static final UUID VISITA_NORTE_COMPLETADA = UUID.fromString("a0000001-0000-4000-8000-000000000006");
    private static final UUID VISITA_SUR_HIGH = UUID.fromString("a0000002-0000-4000-8000-000000000001");

    private static final LocalDate DIA_ORDEN = LocalDate.of(2026, 10, 15);
    private static final LocalDate DIA_NOOP = LocalDate.of(2026, 10, 16);
    private static final LocalDate DIA_REASIGNACION = LocalDate.of(2026, 10, 17);
    private static final LocalDate DIA_FILTROS = LocalDate.of(2026, 10, 19);

    @Test
    @DisplayName("los endpoints de planificación exigen token: 401 sin autenticación")
    void endpointsSinTokenResponden401() throws Exception {
        mockMvc.perform(get("/api/v1/operators"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/operators/{id}/route-sheets", OPERADOR_DEMO)
                        .param("date", DIA_ORDEN.toString()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/visits"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/visits/assign").contentType(APPLICATION_JSON)
                        .content(pedidoAsignacion(OPERADOR_NORTE2, DIA_ORDEN, VISITA_NORTE_HIGH)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("un operador no es supervisor: 403 en los endpoints de planificación")
    void operadorNoAccedeComoSupervisor() throws Exception {
        String token = accessToken("operador.demo", "Operador123!");
        mockMvc.perform(get("/api/v1/operators").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"));
        mockMvc.perform(post("/api/v1/visits/assign").header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(pedidoAsignacion(OPERADOR_DEMO, DIA_ORDEN, VISITA_NORTE_HIGH)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /operators devuelve solo los operadores de la zona del supervisor")
    void listaSoloOperadoresDeSuZona() throws Exception {
        String token = supervisorToken();
        mockMvc.perform(get("/api/v1/operators").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].username").value("operador.demo"))
                .andExpect(jsonPath("$[1].username").value("operador.norte2"))
                .andExpect(jsonPath("$[?(@.username == 'operador.sur')]").isEmpty());
    }

    @Test
    @DisplayName("GET /visits acota las visitas a la jurisdicción del supervisor")
    void soloVeVisitasDeSuJurisdiccion() throws Exception {
        String token = supervisorToken();
        mockMvc.perform(get("/api/v1/visits").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[?(@.code == 'V-2001')]").isEmpty());
    }

    @Test
    @DisplayName("asigna y ordena la hoja de ruta por urgencia (HIGH, MEDIUM, LOW)")
    void asignaYOrdenaPorUrgencia() throws Exception {
        String token = supervisorToken();
        String body = "{\"operatorId\":\"" + OPERADOR_DEMO + "\",\"date\":\"" + DIA_ORDEN
                + "\",\"visitIds\":[\"" + VISITA_NORTE_BAJA + "\",\"" + VISITA_NORTE_MEDIA
                + "\",\"" + VISITA_NORTE_HIGH + "\"]}";

        mockMvc.perform(post("/api/v1/visits/assign").header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operatorUsername").value("operador.demo"))
                .andExpect(jsonPath("$.items[0].position").value(1))
                .andExpect(jsonPath("$.items[0].visit.code").value("V-1001"))
                .andExpect(jsonPath("$.items[1].position").value(2))
                .andExpect(jsonPath("$.items[1].visit.code").value("V-1002"))
                .andExpect(jsonPath("$.items[2].position").value(3))
                .andExpect(jsonPath("$.items[2].visit.code").value("V-1004"));

        mockMvc.perform(get("/api/v1/operators/{id}/route-sheets", OPERADOR_DEMO)
                        .header("Authorization", "Bearer " + token).param("date", DIA_ORDEN.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.items[0].visit.code").value("V-1001"))
                .andExpect(jsonPath("$.items[1].visit.code").value("V-1002"))
                .andExpect(jsonPath("$.items[2].visit.code").value("V-1004"));
    }

    @Test
    @DisplayName("asignar al mismo operador el mismo día es un no-op: 400")
    void noAceptaAsignarDosVecesAlMismoOperador() throws Exception {
        String token = supervisorToken();
        mockMvc.perform(post("/api/v1/visits/assign").header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(pedidoAsignacion(OPERADOR_DEMO, DIA_NOOP, VISITA_NORTE_HIGH)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/visits/assign").header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(pedidoAsignacion(OPERADOR_DEMO, DIA_NOOP, VISITA_NORTE_HIGH)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("visit_already_assigned"));
    }

    @Test
    @DisplayName("reasigna: cambiar de operador el mismo día suelta la hoja anterior")
    void reasignaEntreOperadoresElMismoDia() throws Exception {
        String token = supervisorToken();
        mockMvc.perform(post("/api/v1/visits/assign").header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(pedidoAsignacion(OPERADOR_DEMO, DIA_REASIGNACION, VISITA_NORTE_MEDIA)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/visits/assign").header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(pedidoAsignacion(OPERADOR_NORTE2, DIA_REASIGNACION, VISITA_NORTE_MEDIA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].visit.code").value("V-1002"));

        mockMvc.perform(get("/api/v1/operators/{id}/route-sheets", OPERADOR_DEMO)
                        .header("Authorization", "Bearer " + token).param("date", DIA_REASIGNACION.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));

        mockMvc.perform(get("/api/v1/operators/{id}/route-sheets", OPERADOR_NORTE2)
                        .header("Authorization", "Bearer " + token).param("date", DIA_REASIGNACION.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].visit.code").value("V-1002"));
    }

    @Test
    @DisplayName("acceso horizontal: el supervisor del norte no puede operar la zona sur")
    void accesoHorizontalFueraDeZonaEsForbidden() throws Exception {
        String token = supervisorToken();

        mockMvc.perform(post("/api/v1/visits/assign").header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(pedidoAsignacion(OPERADOR_SUR, DIA_ORDEN, VISITA_SUR_HIGH)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("outside_jurisdiction"));

        mockMvc.perform(get("/api/v1/operators/{id}/route-sheets", OPERADOR_SUR)
                        .header("Authorization", "Bearer " + token).param("date", DIA_ORDEN.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("outside_jurisdiction"));
    }

    @Test
    @DisplayName("operador o visita inexistentes: 404")
    void recursosInexistentesResponden404() throws Exception {
        String token = supervisorToken();
        UUID desconocido = UUID.fromString("99999999-9999-4999-8999-999999999999");

        mockMvc.perform(post("/api/v1/visits/assign").header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(pedidoAsignacion(desconocido, DIA_ORDEN, VISITA_NORTE_HIGH)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("operator_not_found"));

        mockMvc.perform(post("/api/v1/visits/assign").header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(pedidoAsignacion(OPERADOR_DEMO, DIA_ORDEN, desconocido)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("visit_not_found"));
    }

    @Test
    @DisplayName("una visita completada no se puede asignar: 400")
    void visitaCompletadaNoEsAsignable() throws Exception {
        String token = supervisorToken();
        mockMvc.perform(post("/api/v1/visits/assign").header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(pedidoAsignacion(OPERADOR_DEMO, DIA_ORDEN, VISITA_NORTE_COMPLETADA)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("visit_not_assignable"))
                .andExpect(jsonPath("$.message").value("La visita V-1006 no se puede asignar (estado completada)."));
    }

    @Test
    @DisplayName("los filtros de GET /visits combinan estado, urgencia y asignación")
    void filtrosDeVisitas() throws Exception {
        String token = supervisorToken();

        mockMvc.perform(get("/api/v1/visits").header("Authorization", "Bearer " + token)
                        .param("status", "PENDING").param("urgency", "HIGH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].code").value("V-1001"));

        mockMvc.perform(post("/api/v1/visits/assign").header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(pedidoAsignacion(OPERADOR_DEMO, DIA_FILTROS, VISITA_NORTE_MEDIA)))
                .andExpect(status().isOk());
        // Segundo operador con una hoja el MISMO día: el filtro operatorId+date no puede mezclarlos.
        mockMvc.perform(post("/api/v1/visits/assign").header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(pedidoAsignacion(OPERADOR_NORTE2, DIA_FILTROS, VISITA_NORTE_HIGH)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/visits").header("Authorization", "Bearer " + token)
                        .param("operatorId", OPERADOR_DEMO.toString())
                        .param("date", DIA_FILTROS.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].code").value("V-1002"));

        mockMvc.perform(get("/api/v1/visits").header("Authorization", "Bearer " + token)
                        .param("operatorId", OPERADOR_NORTE2.toString())
                        .param("date", DIA_FILTROS.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].code").value("V-1001"));

        // El estado ASSIGNED queda persistido: las dos visitas asignadas ese día (sin mezclar, las
        // de fechas de otros tests no cuentan porque se acota con el filtro date).
        mockMvc.perform(get("/api/v1/visits").header("Authorization", "Bearer " + token)
                        .param("status", "ASSIGNED").param("date", DIA_FILTROS.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    private String supervisorToken() throws Exception {
        return accessToken("supervisor.demo", "Supervisor123!");
    }

    private String accessToken(String username, String password) throws Exception {
        String json = mockMvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.accessToken");
    }

    private static String pedidoAsignacion(UUID operatorId, LocalDate date, UUID... visitIds) {
        String ids = String.join(",",
                java.util.Arrays.stream(visitIds).map(u -> "\"" + u + "\"").toList());
        return "{\"operatorId\":\"" + operatorId + "\",\"date\":\"" + date
                + "\",\"visitIds\":[" + ids + "]}";
    }
}