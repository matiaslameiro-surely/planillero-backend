package ar.com.planillero.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import ar.com.planillero.AbstractIntegrationTest;

/**
 * Pruebas de integración de la finalización de visitas periciales.
 */
@AutoConfigureMockMvc
class VisitCompleteIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private static final UUID OPERADOR_DEMO = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID OPERADOR_NORTE2 = UUID.fromString("44444444-4444-4444-8444-444444444444");

    private static int visitCounter = 0;
    private static final LocalDate DIA_PRUEBA = LocalDate.of(2026, 10, 25);

    @Test
    @DisplayName("el operador completa una visita en curso: 200, status COMPLETED y auditoría registrada")
    void operatorCompletesVisitInProgress() throws Exception {
        UUID visit = newVisit("HIGH");
        assign(OPERADOR_DEMO, DIA_PRUEBA, visit);

        String token = operatorToken("operador.demo");

        // 1. Iniciar la visita
        mockMvc.perform(post("/api/v1/visits/{id}/start", visit)
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"latitude\":-34.6,\"longitude\":-58.4,\"accuracyMeters\":10,\"clientTimestamp\":\"2026-10-25T10:00:00Z\"}"))
                .andExpect(status().isOk());

        // 2. Completar la visita
        mockMvc.perform(post("/api/v1/visits/{id}/complete", visit)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visitId").value(visit.toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.code").isNotEmpty())
                .andExpect(jsonPath("$.completedAt").isNotEmpty());

        assertThat(jdbc.queryForObject(
                "select status from visits.visits where id = ?", String.class, visit))
                .isEqualTo("COMPLETED");

        Integer auditCount = jdbc.queryForObject(
                "select count(*) from audit.audit_logs where event_type = 'VISIT_COMPLETED' and entity_id = ?",
                Integer.class, visit.toString());
        assertThat(auditCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("intentar completar una visita ya completada devuelve 409 visit_already_completed")
    void completeAlreadyCompletedVisitReturns409() throws Exception {
        UUID visit = newVisit("MEDIUM");
        assign(OPERADOR_DEMO, DIA_PRUEBA, visit);
        String token = operatorToken("operador.demo");

        // Iniciar y completar
        mockMvc.perform(post("/api/v1/visits/{id}/start", visit)
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"latitude\":-34.6,\"longitude\":-58.4,\"accuracyMeters\":10,\"clientTimestamp\":\"2026-10-25T10:00:00Z\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/visits/{id}/complete", visit)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Segundo intento
        mockMvc.perform(post("/api/v1/visits/{id}/complete", visit)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("visit_already_completed"));
    }

    @Test
    @DisplayName("intentar completar una visita que no está en curso devuelve 409 visit_not_in_progress")
    void completeVisitNotInProgressReturns409() throws Exception {
        UUID visit = newVisit("LOW");
        assign(OPERADOR_DEMO, DIA_PRUEBA, visit);
        String token = operatorToken("operador.demo");

        // La visita está ASSIGNED pero no iniciada
        mockMvc.perform(post("/api/v1/visits/{id}/complete", visit)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("visit_not_in_progress"));
    }

    @Test
    @DisplayName("un operador no puede completar una visita asignada a otro operador: 403 visit_not_assigned")
    void completeVisitNotAssignedReturns403() throws Exception {
        UUID visit = newVisit("MEDIUM");
        assign(OPERADOR_DEMO, DIA_PRUEBA, visit);

        // Operador distinto intenta completarla
        String tokenNorte2 = operatorToken("operador.norte2");
        mockMvc.perform(post("/api/v1/visits/{id}/complete", visit)
                        .header("Authorization", "Bearer " + tokenNorte2))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("visit_not_assigned"));
    }

    @Test
    @DisplayName("completar una visita inexistente devuelve 404 visit_not_found")
    void completeVisitNotFoundReturns404() throws Exception {
        String token = operatorToken("operador.demo");
        mockMvc.perform(post("/api/v1/visits/{id}/complete", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("visit_not_found"));
    }

    @Test
    @DisplayName("el endpoint exige autenticación y rol OPERATOR")
    void completeRequiresOperatorRole() throws Exception {
        UUID visit = newVisit("LOW");

        // Sin token
        mockMvc.perform(post("/api/v1/visits/{id}/complete", visit))
                .andExpect(status().isUnauthorized());

        // Con rol SUPERVISOR
        mockMvc.perform(post("/api/v1/visits/{id}/complete", visit)
                        .header("Authorization", "Bearer " + supervisorToken()))
                .andExpect(status().isForbidden());
    }

    private UUID newVisit(String urgency) {
        UUID id = UUID.randomUUID();
        String code = "T-COMP-" + (++visitCounter);
        jdbc.update("insert into visits.visits (id, code, address, latitude, longitude, jurisdiction, "
                + "status, urgency) values (?, ?, ?, ?, ?, 'ZONA_NORTE', 'PENDING', ?)",
                id, code, "Calle Ficticia " + visitCounter, -34.5, -58.4, urgency);
        return id;
    }

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
