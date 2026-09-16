package ar.com.planillero.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import ar.com.planillero.security.AuthProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Verifica el control de fuerza bruta del login. */
class LoginAttemptServiceTest {

    private final LoginAttemptService service =
            new LoginAttemptService(new AuthProperties(3, Duration.ofMinutes(15)));

    @Test
    @DisplayName("no bloquea mientras no se supere el máximo de intentos")
    void noBloqueaPorDebajoDelMaximo() {
        service.registerFailure("operador.demo");
        service.registerFailure("operador.demo");

        assertThat(service.isBlocked("operador.demo")).isFalse();
    }

    @Test
    @DisplayName("bloquea al alcanzar el máximo de intentos")
    void bloqueaAlAlcanzarElMaximo() {
        service.registerFailure("operador.demo");
        service.registerFailure("operador.demo");
        service.registerFailure("operador.demo");

        assertThat(service.isBlocked("operador.demo")).isTrue();
    }

    @Test
    @DisplayName("el login exitoso limpia el historial")
    void elResetDesbloquea() {
        service.registerFailure("operador.demo");
        service.registerFailure("operador.demo");
        service.registerFailure("operador.demo");

        service.reset("operador.demo");

        assertThat(service.isBlocked("operador.demo")).isFalse();
    }

    @Test
    @DisplayName("los intentos de un usuario no afectan a otro")
    void losIntentosSonPorClave() {
        service.registerFailure("operador.demo");
        service.registerFailure("operador.demo");
        service.registerFailure("operador.demo");

        assertThat(service.isBlocked("admin.demo")).isFalse();
    }
}
