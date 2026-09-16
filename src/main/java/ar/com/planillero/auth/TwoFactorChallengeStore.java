package ar.com.planillero.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import ar.com.planillero.security.JwtProperties;
import org.springframework.stereotype.Service;

/**
 * Registro de desafíos de segundo factor ya usados.
 *
 * <p>Un desafío sirve una sola vez: quien lo canjea lo marca acá y un segundo intento con el mismo
 * token se rechaza, aunque todavía esté dentro de su ventana de validez. Los identificadores se
 * olvidan cuando vence el desafío, así que el registro no crece más allá de lo que dura un login.
 *
 * <p>Vive en memoria, igual que el control de fuerza bruta: alcanza para una instancia. Con varias
 * instancias detrás de un balanceador habría que compartir el registro (o revalidar contra la base).
 */
@Service
public class TwoFactorChallengeStore {

    private final Duration ttl;
    private final Map<String, Instant> used = new ConcurrentHashMap<>();

    public TwoFactorChallengeStore(JwtProperties properties) {
        this.ttl = properties.challengeTokenTtl();
    }

    /**
     * Marca el desafío como usado.
     *
     * @return {@code true} si lo consumió ahora; {@code false} si ya se había usado
     */
    public boolean tryConsume(String challengeId) {
        purge();
        return used.putIfAbsent(challengeId, Instant.now().plus(ttl)) == null;
    }

    /** Descarta los identificadores cuyo desafío ya venció: a partir de ahí el token no vale. */
    private void purge() {
        Instant now = Instant.now();
        used.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
    }
}
