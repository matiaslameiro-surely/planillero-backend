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
 * con {@code internalProxies} (todas las privadas RFC 1918). Por ello, si un proxy anexara el header
 * en lugar de pisarlo, Tomcat tomaría el valor provisto por el cliente. La seguridad depende
 * estrictamente de que el proxy de borde (NGINX en {@code backoffice/docker/nginx.conf}) pise el header
 * con {@code proxy_set_header X-Forwarded-For $remote_addr;}.
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
     * Criterio 3: Demuestra el comportamiento del backend si un cliente enviara un header falsificado
     * y el proxy intermedio lo anexara (ej. "6.6.6.6, 172.18.0.1") en vez de pisarlo.
     * Tomcat saltea la IP privada 172.18.0.1 (por pertenecer al default de {@code internalProxies}) y
     * termina confiando en 6.6.6.6.
     *
     * <p>Este test deja asentado que el backend por sí solo confía en la cadena que llega de un proxy
     * interno, justificando por qué la defensa real contra spoofing DEBE realizarse en NGINX
     * ({@code backoffice/docker/nginx.conf}) configurando {@code proxy_set_header X-Forwarded-For $remote_addr;}.
     */
    @Test
    void appendedForgedHeaderIsTrustedSoTheEdgeProxyMustOverwriteIt() throws Exception {
        UUID visit = newVisit();
        String supervisorToken = login("supervisor.demo", "Supervisor123!");
        String forgedChain = "6.6.6.6, 172.18.0.1";

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
                .header("X-Forwarded-For", forgedChain)
                .header("X-Real-IP", "172.18.0.1")
                .POST(HttpRequest.BodyPublishers.ofString(assignBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);

        String recordedIp = jdbc.queryForObject(
                "select ip from audit.audit_logs where entity_id = ? and event_type = 'VISIT_ASSIGNED'",
                String.class, visit.toString());

        // Tomcat saltea la IP privada 172.18.0.1 y toma 6.6.6.6
        assertThat(recordedIp).isEqualTo("6.6.6.6");
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
