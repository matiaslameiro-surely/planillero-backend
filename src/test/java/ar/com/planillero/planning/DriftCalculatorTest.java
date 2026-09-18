package ar.com.planillero.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Pruebas del cálculo del desfase entre el reloj del dispositivo y el del servidor. */
class DriftCalculatorTest {

    private static final Instant SERVER = Instant.parse("2026-10-20T15:00:00Z");

    @Test
    @DisplayName("dispositivo atrasado: el desfase es positivo")
    void deviceBehindGivesPositiveDrift() {
        Instant device = Instant.parse("2026-10-20T14:59:30Z");

        assertThat(DriftCalculator.driftSeconds(device, SERVER)).isEqualTo(30);
    }

    @Test
    @DisplayName("dispositivo adelantado: el desfase es negativo")
    void deviceAheadGivesNegativeDrift() {
        Instant device = Instant.parse("2026-10-20T15:00:45Z");

        assertThat(DriftCalculator.driftSeconds(device, SERVER)).isEqualTo(-45);
    }

    @Test
    @DisplayName("relojes iguales: el desfase es cero")
    void sameClockGivesZeroDrift() {
        assertThat(DriftCalculator.driftSeconds(SERVER, SERVER)).isZero();
    }

    @Test
    @DisplayName("una fracción de segundo se redondea al entero más cercano")
    void fractionIsRoundedToNearestSecond() {
        // 0.4 s de atraso -> 0; 0.6 s de atraso -> 1.
        assertThat(DriftCalculator.driftSeconds(SERVER.minusMillis(400), SERVER)).isZero();
        assertThat(DriftCalculator.driftSeconds(SERVER.minusMillis(600), SERVER)).isEqualTo(1);
        // Adelantado: -0.4 s -> 0; -0.6 s -> -1. Truncar daría 0 en los dos casos.
        assertThat(DriftCalculator.driftSeconds(SERVER.plusMillis(400), SERVER)).isZero();
        assertThat(DriftCalculator.driftSeconds(SERVER.plusMillis(600), SERVER)).isEqualTo(-1);
    }

    @Test
    @DisplayName("un desfase de días se calcula sin desbordar")
    void largeDriftIsComputed() {
        Instant device = SERVER.minusSeconds(3 * 24 * 3600);

        assertThat(DriftCalculator.driftSeconds(device, SERVER)).isEqualTo(3 * 24 * 3600);
    }

    @Test
    @DisplayName("una hora imposible de representar se rechaza en vez de desbordar")
    void impossibleTimeIsRejected() {
        assertThatThrownBy(() -> DriftCalculator.driftSeconds(Instant.MIN, SERVER))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
