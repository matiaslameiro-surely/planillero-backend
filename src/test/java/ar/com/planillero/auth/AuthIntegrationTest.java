package ar.com.planillero.auth;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.com.planillero.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Pruebas de integración de la autenticación.
 *
 * <p>Corren contra un PostgreSQL real (Testcontainers) y con la cadena de seguridad completa, así
 * que validan login, rotación de refresh tokens, RBAC y segundo factor de punta a punta.
 *
 * <p>Ojo con el orden de las operaciones: habilitar el segundo factor queda persistido. El flujo de
 * 2FA usa {@code supervisor.demo}, que ningún otro test toca, para no romper a los demás si se
 * reordena la ejecución.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /salud es público")
    void saludEsPublica() throws Exception {
        mockMvc.perform(get("/salud"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ok"));
    }

    @Test
    @DisplayName("POST /auth/login con credenciales inválidas responde 401 sin revelar el campo")
    void loginInvalidoResponde401() throws Exception {
        mockMvc.perform(post("/auth/login").contentType(APPLICATION_JSON)
                        .content(credenciales("operador.demo", "clave-incorrecta")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_credentials"));
    }

    @Test
    @DisplayName("POST /auth/login con credenciales válidas devuelve access y refresh token")
    void loginValidoDevuelveTokens() throws Exception {
        mockMvc.perform(post("/auth/login").contentType(APPLICATION_JSON)
                        .content(credenciales("operador.demo", "Operador123!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.twoFactorRequired").value(false))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    @DisplayName("GET /auth/me sin token responde 401")
    void meSinTokenResponde401() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    @DisplayName("un operario recibe 403 en el endpoint de administrador y un admin entra")
    void rbacPorRol() throws Exception {
        String tokenOperador = accessToken("operador.demo", "Operador123!");
        mockMvc.perform(get("/roles/ejemplo-admin").header("Authorization", "Bearer " + tokenOperador))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"));

        String tokenAdmin = accessToken("admin.demo", "Admin123!");
        mockMvc.perform(get("/roles/ejemplo-admin").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk());
        mockMvc.perform(get("/roles/ejemplo-operador").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /auth/refresh rota el token: el viejo deja de servir")
    void refreshRotaYRevocaElTokenAnterior() throws Exception {
        String refreshToken = refreshToken("operador.demo", "Operador123!");

        String nuevoRefresh = JsonPath.read(
                mockMvc.perform(post("/auth/refresh").contentType(APPLICATION_JSON)
                                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                "$.refreshToken");

        mockMvc.perform(post("/auth/refresh").contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_refresh_token"));

        mockMvc.perform(post("/auth/refresh").contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + nuevoRefresh + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("con el segundo factor habilitado el login pide el código y luego entrega los tokens")
    void flujoDeSegundoFactor() throws Exception {
        String token = accessToken("supervisor.demo", "Supervisor123!");

        String secreto = JsonPath.read(
                mockMvc.perform(post("/auth/2fa/setup").header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                "$.secret");

        mockMvc.perform(post("/auth/2fa/enable").header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"code\":\"" + codigoVigente(secreto) + "\"}"))
                .andExpect(status().isNoContent());

        MvcResult login = mockMvc.perform(post("/auth/login").contentType(APPLICATION_JSON)
                        .content(credenciales("supervisor.demo", "Supervisor123!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.twoFactorRequired").value(true))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andReturn();

        String challengeId = JsonPath.read(login.getResponse().getContentAsString(), "$.challengeId");

        mockMvc.perform(post("/auth/verify-2fa").contentType(APPLICATION_JSON)
                        .content("{\"challengeId\":\"" + challengeId + "\",\"code\":\""
                                + codigoVigente(secreto) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    private String accessToken(String username, String password) throws Exception {
        String json = mockMvc.perform(post("/auth/login").contentType(APPLICATION_JSON)
                        .content(credenciales(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.accessToken");
    }

    private String refreshToken(String username, String password) throws Exception {
        String json = mockMvc.perform(post("/auth/login").contentType(APPLICATION_JSON)
                        .content(credenciales(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.refreshToken");
    }

    private static String credenciales(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    private static String codigoVigente(String secret) throws Exception {
        long counter = Instant.now().getEpochSecond() / 30;
        return new DefaultCodeGenerator().generate(secret, counter);
    }
}
