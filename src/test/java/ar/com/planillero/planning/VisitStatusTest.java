package ar.com.planillero.planning;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Pruebas del nombre en español de los estados de una visita, que se usa en los mensajes de error. */
class VisitStatusTest {

    @Test
    @DisplayName("cada estado se nombra con las mismas palabras que usan el backoffice y la app")
    void labelsMatchTheClients() {
        assertThat(VisitStatus.PENDING.label()).isEqualTo("pendiente");
        assertThat(VisitStatus.ASSIGNED.label()).isEqualTo("asignada");
        assertThat(VisitStatus.IN_PROGRESS.label()).isEqualTo("en curso");
        assertThat(VisitStatus.COMPLETED.label()).isEqualTo("completada");
        assertThat(VisitStatus.CANCELLED.label()).isEqualTo("cancelada");
    }

    @Test
    @DisplayName("ningún estado tiene el nombre vacío ni repite el del enum")
    void everyStatusHasASpanishLabel() {
        assertThat(Arrays.stream(VisitStatus.values()))
                .allSatisfy(status -> {
                    assertThat(status.label()).isNotBlank();
                    assertThat(status.label()).isNotEqualToIgnoringCase(status.name());
                });
    }
}
