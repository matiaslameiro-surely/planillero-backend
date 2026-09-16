package ar.com.planillero.auth;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import ar.com.planillero.security.AuthProperties;
import org.springframework.stereotype.Service;

/**
 * Control de fuerza bruta sobre el login.
 *
 * <p>Cuenta los intentos fallidos por clave (el nombre de usuario) dentro de una ventana de tiempo.
 * Al superar el límite, el login se frena con 429 hasta que la ventana se vacíe. Se guarda en
 * memoria: alcanza para una instancia y no ensucia la base con intentos que expiran solos.
 */
@Service
public class LoginAttemptService {

    private final AuthProperties properties;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public LoginAttemptService(AuthProperties properties) {
        this.properties = properties;
    }

    /** ¿La clave superó el máximo de intentos fallidos dentro de la ventana? */
    public boolean isBlocked(String key) {
        return recent(key).size() >= properties.maxAttempts();
    }

    /** Registra un intento fallido. */
    public void registerFailure(String key) {
        Deque<Instant> attempts = recent(key);
        synchronized (attempts) {
            attempts.addLast(Instant.now());
        }
    }

    /** Limpia el historial tras un login exitoso. */
    public void reset(String key) {
        failures.remove(key);
    }

    /** Intentos que caen dentro de la ventana vigente, descartando los viejos. */
    private Deque<Instant> recent(String key) {
        Instant cutoff = Instant.now().minus(properties.attemptWindow());
        Deque<Instant> attempts = failures.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (attempts) {
            while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) {
                attempts.pollFirst();
            }
        }
        return attempts;
    }
}
