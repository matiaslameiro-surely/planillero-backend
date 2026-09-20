package ar.com.planillero.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import ar.com.planillero.AbstractIntegrationTest;

/**
 * Pruebas de integración de la auditoría: encadenamiento de hashes, inmutabilidad de la tabla y
 * control de acceso de los endpoints de consulta y verificación. Corren contra PostgreSQL real
 * (Testcontainers), así los triggers de {@code V11} se ejercitan de verdad.
 */
@AutoConfigureMockMvc
class AuditIntegrationTest extends AbstractIntegrationTest {

    private static final UUID OPERADOR_DEMO = UUID.fromString("11111111-1111-4111-8111-111111111111");

    private static int visitCounter = 0;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void startingAVisitWritesAChainedAuditRowThatVerifiesOk() throws Exception {
        UUID visit = newVisit();
        assign(OPERADOR_DEMO, LocalDate.of(2026, 11, 10), visit);
        start(visit, "2026-11-10T15:00:00Z");

        Integer rows = jdbc.queryForObject(
                "select count(*) from audit.audit_logs where entity_id = ? and event_type = 'VISIT_STARTED'",
                Integer.class, visit.toString());
        assertThat(rows).isEqualTo(1);

        String username = jdbc.queryForObject(
                "select username from audit.audit_logs where entity_id = ? and event_type = 'VISIT_STARTED'",
                String.class, visit.toString());
        assertThat(username).isEqualTo("operador.demo");

        mockMvc.perform(get("/api/v1/audit/verify").param("visitId", visit.toString())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intacta").value(true));

        mockMvc.perform(get("/api/v1/audit/verify")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intacta").value(true));
    }

    @Test
    void secondEventChainsAgainstTheFirstOnesHash() throws Exception {
        UUID visitA = newVisit();
        UUID visitB = newVisit();
        assign(OPERADOR_DEMO, LocalDate.of(2026, 11, 11), visitA, visitB);
        start(visitA, "2026-11-11T15:00:00Z");
        start(visitB, "2026-11-11T15:05:00Z");

        String hashAfterFirst = jdbc.queryForObject(
                "select hash_actual from audit.audit_logs where entity_id = ? order by created_at asc limit 1",
                String.class, visitA.toString());
        String previousHashOfSecond = jdbc.queryForObject(
                "select hash_previo from audit.audit_logs where entity_id = ? order by created_at asc limit 1",
                String.class, visitB.toString());

        assertThat(previousHashOfSecond).isEqualTo(hashAfterFirst);
    }

    @Test
    void auditLogsTableRejectsDirectUpdateAndDelete() throws Exception {
        UUID visit = newVisit();
        assign(OPERADOR_DEMO, LocalDate.of(2026, 11, 12), visit);
        start(visit, "2026-11-12T15:00:00Z");

        assertThatThrownBy(() -> jdbc.update(
                "update audit.audit_logs set username = 'tampered' where entity_id = ?", visit.toString()))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbc.update(
                "delete from audit.audit_logs where entity_id = ?", visit.toString()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void onlyAdministratorCanQueryAudit() throws Exception {
        mockMvc.perform(get("/api/v1/audit/logs"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/audit/logs").header("Authorization", "Bearer " + operatorToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/audit/logs").header("Authorization", "Bearer " + supervisorToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/audit/logs").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/audit/verify").header("Authorization", "Bearer " + operatorToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void logsCanBeFilteredByEventType() throws Exception {
        UUID visit = newVisit();
        assign(OPERADOR_DEMO, LocalDate.of(2026, 11, 13), visit);
        start(visit, "2026-11-13T15:00:00Z");

        String json = mockMvc.perform(get("/api/v1/audit/logs").param("eventType", "VISIT_STARTED")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        java.util.List<String> eventTypes = JsonPath.read(json, "$.content[*].eventType");
        java.util.List<String> entityIds = JsonPath.read(json, "$.content[*].entityId");
        assertThat(eventTypes).allMatch("VISIT_STARTED"::equals);
        assertThat(entityIds).contains(visit.toString());
    }

    private UUID newVisit() {
        UUID id = UUID.randomUUID();
        String code = "T-AUDIT-" + (++visitCounter);
        jdbc.update("insert into visits.visits (id, code, address, latitude, longitude, jurisdiction, "
                + "status, urgency) values (?, ?, ?, ?, ?, 'ZONA_NORTE', 'PENDING', 'MEDIUM')",
                id, code, "Calle Ficticia " + visitCounter, -34.5, -58.4);
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

    private void start(UUID visitId, String clientTimestamp) throws Exception {
        String body = "{\"latitude\":-34.6,\"longitude\":-58.4,\"accuracyMeters\":8,\"clientTimestamp\":\""
                + clientTimestamp + "\"}";
        mockMvc.perform(post("/api/v1/visits/{id}/start", visitId)
                        .header("Authorization", "Bearer " + operatorToken())
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private String supervisorToken() throws Exception {
        return accessToken("supervisor.demo", "Supervisor123!");
    }

    private String operatorToken() throws Exception {
        return accessToken("operador.demo", "Operador123!");
    }

    private String adminToken() throws Exception {
        return accessToken("admin.demo", "Admin123!");
    }

    private String accessToken(String username, String password) throws Exception {
        String json = mockMvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.accessToken");
    }
}
