package ar.com.planillero.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import ar.com.planillero.security.JwtProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Verifica que el desafío de segundo factor se consuma una sola vez. */
class TwoFactorChallengeStoreTest {

    @Test
    @DisplayName("un desafío se consume una vez y el segundo intento se rechaza")
    void noSePuedeConsumirDosVeces() {
        TwoFactorChallengeStore store = new TwoFactorChallengeStore(propiedades(Duration.ofMinutes(5)));

        assertThat(store.tryConsume("desafio-1")).isTrue();
        assertThat(store.tryConsume("desafio-1")).isFalse();
    }

    @Test
    @DisplayName("desafíos distintos no se estorban entre sí")
    void cadaDesafioEsIndependiente() {
        TwoFactorChallengeStore store = new TwoFactorChallengeStore(propiedades(Duration.ofMinutes(5)));

        assertThat(store.tryConsume("desafio-1")).isTrue();
        assertThat(store.tryConsume("desafio-2")).isTrue();
    }

    @Test
    @DisplayName("un identificador vencido se olvida y no queda ocupando lugar")
    void losIdentificadoresVencidosSeDescartan() throws Exception {
        TwoFactorChallengeStore store = new TwoFactorChallengeStore(propiedades(Duration.ofMillis(10)));

        assertThat(store.tryConsume("desafio-1")).isTrue();
        Thread.sleep(30);

        assertThat(store.tryConsume("desafio-1")).isTrue();
    }

    private static JwtProperties propiedades(Duration challengeTtl) {
        return new JwtProperties(
                "planillero-backend",
                Duration.ofMinutes(15),
                Duration.ofDays(7),
                challengeTtl,
                null,
                null);
    }
}
