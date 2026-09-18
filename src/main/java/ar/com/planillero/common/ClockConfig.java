package ar.com.planillero.common;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj de la aplicación.
 *
 * <p>Se inyecta en vez de llamar a {@code Instant.now()}, así los tests que dependen de la hora del
 * servidor (como el desfase del inicio de visita) pueden fijarla. En producción es el reloj del
 * sistema, que se asume sincronizado por NTP a nivel de infraestructura.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
