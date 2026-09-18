package ar.com.planillero.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.com.planillero.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pruebas de integración del inicio de visita y de la agenda del operador.
 *
 * <p>Corren contra un PostgreSQL real (Testcontainers) con la cadena de seguridad completa. La hora del
 * servidor está fijada, así el desfase es determinístico. Cada prueba usa sus propias visitas y fechas
 * para no depender del orden de ejecución.
 */
@AutoConfigureMockMvc
@Import(VisitStartIntegrationTest.FixedClockConfig.class)
class VisitStartIntegrationTest extends AbstractIntegrationTest {

    private static final Instant SERVER_NOW = Instant.parse("2026-10-20T15:00:00Z");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(SERVER_NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private static final UUID OPERADOR_DEMO = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID OPERADOR_NORTE2 = UUID.fromString("44444444-4444-4444-8444-444444444444");

    private static int visitCounter = 0;

    private static final LocalDate DIA_INICIO = LocalDate.of(2026, 10, 20);
    private static final LocalDate DIA_AGENDA = LocalDate.of(2026, 11, 1);

    @Test
    @DisplayName("el operador inicia su visita: 200, se persiste la evidencia y el desfase sale del reloj del servidor")
    void operatorStartsAssignedVisit() throws Exception {
        UUID visit = newVisit("HIGH");
        assign(OPERADOR_DEMO, DIA_INICIO, visit);
        // Dispositivo atrasado 30 segundos respecto del servidor.
        String body = startBody("-34.603712", "-58.381593", "8.5", "2026-10-20T14:59:30Z");

        mockMvc.perform(post("/api/v1/visits/{id}/start", visit)
                        .header("Authorization", "Bearer " + operatorToken("operador.demo"))
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visitId").value(visit.toString()))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.latitude").value(-34.603712))
                .andExpect(jsonPath("$.longitude").value(-58.381593))
                .andExpect(jsonPath("$.accuracyMeters").value(8.5))
                .andExpect(jsonPath("$.startedAtDevice").value("2026-10-20T14:59:30Z"))
                .andExpect(jsonPath("$.startedAtServer").value("2026-10-20T15:00:00Z"))
                .andExpect(jsonPath("$.driftSeconds").value(30));

        assertThat(jdbc.queryForObject(
                "select status from visits.visits where id = ?", String.class, visit))
                .isEqualTo("IN_PROGRESS");
        assertThat(jdbc.queryForObject(
                "select drift_seconds from visits.visits where id = ?", Long.class, visit))
                .isEqualTo(30L);
        assertThat(jdbc.queryForObject(
                "select started_by from visits.visits where id = ?", UUID.class, visit))
                .isEqualTo(OPERADOR_DEMO);
    }

    @Test
    @DisplayName("un dispositivo adelantado da un desfase negativo")
    void aheadDeviceGivesNegativeDrift() throws Exception {
        UUID visit = newVisit("LOW");
        assign(OPERADOR_DEMO, DIA_INICIO.plusDays(10), visit);

        mockMvc.perform(post("/api/v1/visits/{id}/start", visit)
                        .header("Authorization", "Bearer " + operatorToken("operador.demo"))
                        .contentType(APPLICATION_JSON)
                        .content(startBody("-34.6", "-58.4", "20", "2026-10-20T15:01:00Z")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driftSeconds").value(-60));
    }

    @Test
    @DisplayName("un pedido inválido responde 400 con el campo en el mensaje")
    void invalidRequestReturns400WithField() throws Exception {
        String token = operatorToken("operador.demo");

        expectInvalid(token, startBody(null, "-58.4", "8", "2026-10-20T15:00:00Z"), "latitude");
        expectInvalid(token, startBody("-34.6", null, "8", "2026-10-20T15:00:00Z"), "longitude");
        expectInvalid(token, startBody("-34.6", "-58.4", null, "2026-10-20T15:00:00Z"), "accuracyMeters");
        expectInvalid(token, startBody("-34.6", "-58.4", "8", null), "clientTimestamp");
        expectInvalid(token, startBody("90.1", "-58.4", "8", "2026-10-20T15:00:00Z"), "latitude");
        expectInvalid(token, startBody("-90.1", "-58.4", "8", "2026-10-20T15:00:00Z"), "latitude");
        expectInvalid(token, startBody("-34.6", "180.1", "8", "2026-10-20T15:00:00Z"), "longitude");
        expectInvalid(token, startBody("-34.6", "-180.1", "8", "2026-10-20T15:00:00Z"), "longitude");
        expectInvalid(token, startBody("-34.6", "-58.4", "-1", "2026-10-20T15:00:00Z"), "accuracyMeters");
    }

    @Test
    @DisplayName("los límites de latitud y longitud se aceptan")
    void coordinateLimitsAreAccepted() throws Exception {
        UUID visit = newVisit("LOW");
        assign(OPERADOR_DEMO, DIA_INICIO.plusDays(11), visit);

        mockMvc.perform(post("/api/v1/visits/{id}/start", visit)
                        .header("Authorization", "Bearer " + operatorToken("operador.demo"))
                        .contentType(APPLICATION_JSON)
                        .content(startBody("90", "-180", "0", "2026-10-20T15:00:00Z")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driftSeconds").value(0));
    }

    @Test
    @DisplayName("sin token responde 401, y un supervisor no puede iniciar visitas: 403")
    void authenticationAndRoleAreRequired() throws Exception {
        UUID visit = newVisit("LOW");
        String body = startBody("-34.6", "-58.4", "8", "2026-10-20T15:00:00Z");

        mockMvc.perform(post("/api/v1/visits/{id}/start", visit)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/visits/{id}/start", visit)
                        .header("Authorization", "Bearer " + supervisorToken())
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"));
    }

    @Test
    @DisplayName("una visita que no existe responde 404")
    void unknownVisitReturns404() throws Exception {
        mockMvc.perform(post("/api/v1/visits/{id}/start", UUID.randomUUID())
                        .header("Authorization", "Bearer " + operatorToken("operador.demo"))
                        .contentType(APPLICATION_JSON)
                        .content(startBody("-34.6", "-58.4", "8", "2026-10-20T15:00:00Z")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("visit_not_found"));
    }

    @Test
    @DisplayName("una visita de otro operador, o sin asignar, responde 403 y no se modifica")
    void foreignOrUnassignedVisitReturns403() throws Exception {
        // La visita es del operador norte2; operador.demo no puede iniciarla.
        UUID visit = newVisit("MEDIUM");
        assign(OPERADOR_NORTE2, DIA_INICIO.plusDays(1), visit);
        String body = startBody("-34.6", "-58.4", "8", "2026-10-20T15:00:00Z");
        String token = operatorToken("operador.demo");

        mockMvc.perform(post("/api/v1/visits/{id}/start", visit)
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("visit_not_assigned"));

        assertThat(jdbc.queryForObject(
                "select status from visits.visits where id = ?", String.class, visit))
                .isEqualTo("ASSIGNED");
        assertThat(jdbc.queryForObject(
                "select started_at_server from visits.visits where id = ?", Instant.class, visit))
                .isNull();
    }

    @Test
    @DisplayName("iniciar una visita ya iniciada responde 409 y no pisa los datos del primer inicio")
    void secondStartReturns409AndKeepsFirstData() throws Exception {
        UUID visit = newVisit("MEDIUM");
        assign(OPERADOR_DEMO, DIA_INICIO.plusDays(2), visit);
        String token = operatorToken("operador.demo");

        mockMvc.perform(post("/api/v1/visits/{id}/start", visit)
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(startBody("-34.603712", "-58.381593", "8", "2026-10-20T14:59:30Z")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/visits/{id}/start", visit)
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(startBody("10", "20", "99", "2026-10-20T10:00:00Z")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("visit_not_startable"));

        assertThat(jdbc.queryForObject(
                "select start_latitude from visits.visits where id = ?", java.math.BigDecimal.class, visit))
                .isEqualByComparingTo("-34.603712");
        assertThat(jdbc.queryForObject(
                "select drift_seconds from visits.visits where id = ?", Long.class, visit))
                .isEqualTo(30L);
    }

    @Test
    @DisplayName("una visita ya iniciada no se puede reasignar: 400 y sigue IN_PROGRESS")
    void startedVisitCannotBeReassigned() throws Exception {
        UUID visit = newVisit("HIGH");
        assign(OPERADOR_DEMO, DIA_INICIO.plusDays(4), visit);
        mockMvc.perform(post("/api/v1/visits/{id}/start", visit)
                        .header("Authorization", "Bearer " + operatorToken("operador.demo"))
                        .contentType(APPLICATION_JSON)
                        .content(startBody("-34.6", "-58.4", "8", "2026-10-20T14:59:30Z")))
                .andExpect(status().isOk());

        String body = "{\"operatorId\":\"" + OPERADOR_NORTE2 + "\",\"date\":\""
                + DIA_INICIO.plusDays(5) + "\",\"visitIds\":[\"" + visit + "\"]}";
        mockMvc.perform(post("/api/v1/visits/assign")
                        .header("Authorization", "Bearer " + supervisorToken())
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("visit_not_assignable"));

        assertThat(jdbc.queryForObject(
                "select status from visits.visits where id = ?", String.class, visit))
                .isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("dos inicios simultáneos de la misma visita: uno responde 200 y el otro 409")
    void concurrentStartsHaveOneWinner() throws Exception {
        UUID visit = newVisit("HIGH");
        assign(OPERADOR_DEMO, DIA_INICIO.plusDays(3), visit);
        String token = operatorToken("operador.demo");
        String body = startBody("-34.6", "-58.4", "8", "2026-10-20T14:59:30Z");

        int callers = 2;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (int i = 0; i < callers; i++) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return mockMvc.perform(post("/api/v1/visits/{id}/start", visit)
                                    .header("Authorization", "Bearer " + token)
                                    .contentType(APPLICATION_JSON).content(body))
                            .andReturn().getResponse().getStatus();
                }));
            }
            ready.await();
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("GET /operators/me/route-sheet devuelve sólo la hoja del operador autenticado, ordenada")
    void myRouteSheetReturnsOnlyOwnVisits() throws Exception {
        // Alta urgencia primero: se asigna primero la LOW y después la HIGH.
        UUID low = newVisit("LOW");
        UUID high = newVisit("HIGH");
        String lowCode = codeOf(low);
        String highCode = codeOf(high);
        assign(OPERADOR_DEMO, DIA_AGENDA, low, high);

        mockMvc.perform(get("/api/v1/operators/me/route-sheet").param("date", DIA_AGENDA.toString())
                        .header("Authorization", "Bearer " + operatorToken("operador.demo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operatorUsername").value("operador.demo"))
                .andExpect(jsonPath("$.date").value(DIA_AGENDA.toString()))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].position").value(1))
                .andExpect(jsonPath("$.items[0].visit.code").value(highCode))
                .andExpect(jsonPath("$.items[1].visit.code").value(lowCode));

        // Otro operador, la misma fecha: no ve nada de lo ajeno.
        mockMvc.perform(get("/api/v1/operators/me/route-sheet").param("date", DIA_AGENDA.toString())
                        .header("Authorization", "Bearer " + operatorToken("operador.norte2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operatorUsername").value("operador.norte2"))
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    @DisplayName("la agenda del operador exige fecha válida, token y rol de operador")
    void myRouteSheetValidatesInputAndRole() throws Exception {
        String token = operatorToken("operador.demo");

        mockMvc.perform(get("/api/v1/operators/me/route-sheet")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/operators/me/route-sheet").param("date", "20/10/2026")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/operators/me/route-sheet").param("date", DIA_AGENDA.toString()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/operators/me/route-sheet").param("date", DIA_AGENDA.toString())
                        .header("Authorization", "Bearer " + supervisorToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("una visita iniciada aparece como IN_PROGRESS en la agenda del operador")
    void startedVisitShowsInAgenda() throws Exception {
        LocalDate day = DIA_AGENDA.plusDays(5);
        UUID visit = newVisit("MEDIUM");
        assign(OPERADOR_NORTE2, day, visit);
        String token = operatorToken("operador.norte2");

        mockMvc.perform(post("/api/v1/visits/{id}/start", visit)
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(startBody("-34.6", "-58.4", "8", "2026-10-20T14:59:30Z")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/operators/me/route-sheet").param("date", day.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].visit.status").value("IN_PROGRESS"));
    }

    private void expectInvalid(String token, String body, String field) throws Exception {
        mockMvc.perform(post("/api/v1/visits/{id}/start", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(field)));
    }

    /** Crea una visita PENDING de la zona norte con datos ficticios y un código único. */
    private UUID newVisit(String urgency) {
        UUID id = UUID.randomUUID();
        String code = "T-START-" + (++visitCounter);
        jdbc.update("insert into visits.visits (id, code, address, latitude, longitude, jurisdiction, "
                + "status, urgency) values (?, ?, ?, ?, ?, 'ZONA_NORTE', 'PENDING', ?)",
                id, code, "Calle Ficticia " + visitCounter, -34.5, -58.4, urgency);
        return id;
    }

    private String codeOf(UUID visitId) {
        return jdbc.queryForObject("select code from visits.visits where id = ?", String.class, visitId);
    }

    /** Asigna visitas a un operador usando la API real del supervisor, como lo haría el backoffice. */
    private void assign(UUID operatorId, LocalDate date, UUID... visitIds) throws Exception {
        String ids = String.join(",",
                java.util.Arrays.stream(visitIds).map(u -> "\"" + u + "\"").toList());
        String body = "{\"operatorId\":\"" + operatorId + "\",\"date\":\"" + date
                + "\",\"visitIds\":[" + ids + "]}";
        mockMvc.perform(post("/api/v1/visits/assign")
                        .header("Authorization", "Bearer " + supervisorToken())
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private static String startBody(String latitude, String longitude, String accuracy, String clientTimestamp) {
        List<String> fields = new ArrayList<>();
        if (latitude != null) {
            fields.add("\"latitude\":" + latitude);
        }
        if (longitude != null) {
            fields.add("\"longitude\":" + longitude);
        }
        if (accuracy != null) {
            fields.add("\"accuracyMeters\":" + accuracy);
        }
        if (clientTimestamp != null) {
            fields.add("\"clientTimestamp\":\"" + clientTimestamp + "\"");
        }
        return "{" + String.join(",", fields) + "}";
    }

    private String supervisorToken() throws Exception {
        return accessToken("supervisor.demo", "Supervisor123!");
    }

    private String operatorToken(String username) throws Exception {
        return accessToken(username, "Operador123!");
    }

    private String accessToken(String username, String password) throws Exception {
        String json = mockMvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.accessToken");
    }
}
