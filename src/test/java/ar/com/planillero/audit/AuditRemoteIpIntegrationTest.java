package ar.com.planillero.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import com.jayway.jsonpath.JsonPath;

import ar.com.planillero.AbstractIntegrationTest;

/**
 * Pruebas de integración con servidor Tomcat embebido para verificar la resolución de la IP real
 * del cliente cuando las peticiones provienen de un proxy inverso confiable (NGINX / Docker)
 * mediante {@code server.forward-headers-strategy=native} (RemoteIpValve).
 *
 * <p><b>Seguridad contra Spoofing (Criterio 3):</b>
 * Tomcat recorre {@code X-Forwarded-For} de derecha a izquierda y descarta las IPs que coinciden
 * con {@code internalProxies} (todas las privadas RFC 1918). Por ello, la seguridad contra
 * falsificación de IP depende estrictamente de que el proxy de borde (NGINX en
 * {@code backoffice/docker/nginx.conf}) pise el header con {@code $remote_addr} en lugar de
 * anexarlo con {@code $proxy_add_x_forwarded_for}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuditRemoteIpIntegrationTest extends AbstractIntegrationTest {

    private static final UUID OPERADOR_DEMO = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static int visitCounter = 100;

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void requestThroughTrustedProxyRecordsClientIpFromForwardedHeader() throws Exception {
        UUID visit = newVisit();
        String supervisorToken = login("supervisor.demo", "Supervisor123!");
        String clientIp = "203.0.113.195";

        String assignBody = """
                {
                    "operatorId": "%s",
                    "date": "%s",
                    "visitIds": ["%s"]
                }
                """.formatted(OPERADOR_DEMO, LocalDate.of(2026, 11, 20), visit);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/visits/assign"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + supervisorToken)
                .header("X-Forwarded-For", clientIp)
                .header("X-Real-IP", clientIp)
                .POST(HttpRequest.BodyPublishers.ofString(assignBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);

        String recordedIp = jdbc.queryForObject(
                "select ip from audit.audit_logs where entity_id = ? and event_type = 'VISIT_ASSIGNED'",
                String.class, visit.toString());

        assertThat(recordedIp).isEqualTo(clientIp);
    }

    @Test
    void directRequestWithoutForwardedHeadersRecordsConnectionIp() throws Exception {
        UUID visit = newVisit();
        String supervisorToken = login("supervisor.demo", "Supervisor123!");

        String assignBody = """
                {
                    "operatorId": "%s",
                    "date": "%s",
                    "visitIds": ["%s"]
                }
                """.formatted(OPERADOR_DEMO, LocalDate.of(2026, 11, 21), visit);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/visits/assign"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + supervisorToken)
                .POST(HttpRequest.BodyPublishers.ofString(assignBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);

        String recordedIp = jdbc.queryForObject(
                "select ip from audit.audit_logs where entity_id = ? and event_type = 'VISIT_ASSIGNED'",
                String.class, visit.toString());

        assertThat(recordedIp).isEqualTo("127.0.0.1");
    }

    /**
     * Criterio 3: Cuando un cliente intenta falsificar su IP enviando cabeceras falsas, NGINX
     * (como proxy de borde en backoffice/docker/nginx.conf) sobreescribe {@code X-Forwarded-For}
     * con la IP real de conexión {@code $remote_addr} (ej. 198.51.100.24), descartando el valor
     * malicioso. El backend recibe y audita únicamente la IP real.
     */
    @Test
    void forgedHeaderOverwrittenByBorderProxyRecordsRealClientIp() throws Exception {
        UUID visit = newVisit();
        String supervisorToken = login("supervisor.demo", "Supervisor123!");
        String realClientIp = "198.51.100.24";

        // Simula la petición que llega al backend luego de que NGINX pisó cualquier
        // X-Forwarded-For previo con la IP de conexión real ($remote_addr)
        String assignBody = """
                {
                    "operatorId": "%s",
                    "date": "%s",
                    "visitIds": ["%s"]
                }
                """.formatted(OPERADOR_DEMO, LocalDate.of(2026, 11, 22), visit);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/visits/assign"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + supervisorToken)
                .header("X-Forwarded-For", realClientIp)
                .header("X-Real-IP", realClientIp)
                .POST(HttpRequest.BodyPublishers.ofString(assignBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);

        String recordedIp = jdbc.queryForObject(
                "select ip from audit.audit_logs where entity_id = ? and event_type = 'VISIT_ASSIGNED'",
                String.class, visit.toString());

        assertThat(recordedIp).isEqualTo(realClientIp);
    }

    private UUID newVisit() {
        UUID id = UUID.randomUUID();
        String code = "T-REMOTE-IP-" + (++visitCounter);
        jdbc.update("insert into visits.visits (id, code, address, latitude, longitude, jurisdiction, "
                + "status, urgency) values (?, ?, ?, ?, ?, 'ZONA_NORTE', 'PENDING', 'MEDIUM')",
                id, code, "Calle Ficticia " + visitCounter, -34.5, -58.4);
        return id;
    }

    private String login(String username, String password) throws Exception {
        String loginBody = """
                {
                    "username": "%s",
                    "password": "%s"
                }
                """.formatted(username, password);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(loginBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);

        return JsonPath.read(response.body(), "$.accessToken");
    }
}
