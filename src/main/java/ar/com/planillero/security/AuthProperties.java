package ar.com.planillero.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del control de fuerza bruta sobre el login.
 *
 * @param maxAttempts   intentos fallidos permitidos dentro de la ventana
 * @param attemptWindow ventana de tiempo en la que se cuentan los intentos
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(int maxAttempts, Duration attemptWindow) {
}
