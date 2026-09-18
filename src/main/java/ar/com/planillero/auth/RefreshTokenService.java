package ar.com.planillero.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import ar.com.planillero.common.ApiException;
import ar.com.planillero.security.JwtProperties;
import ar.com.planillero.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emite, rota y revoca refresh tokens.
 *
 * <p>El valor que recibe el cliente es aleatorio y no se guarda: en la base queda sólo su hash
 * SHA-256. Rota en cada uso —el token consumido se revoca y se emite uno nuevo— para que un token
 * robado y reusado quede sin efecto.
 */
@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository repository;
    private final JwtProperties properties;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository, JwtProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /** Emite un refresh token nuevo para el usuario y devuelve su valor en claro (una sola vez). */
    @Transactional
    public String issue(User user) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = Instant.now().plus(properties.refreshTokenTtl());
        repository.save(new RefreshToken(user, hash(raw), expiresAt));
        return raw;
    }

    /** Consume el token recibido y devuelve su usuario; el token viejo queda revocado. */
    @Transactional
    public User consume(String raw) {
        RefreshToken token = require(raw);
        token.revoke();
        repository.save(token);
        return token.getUser();
    }

    /** Revoca un token si existe. No falla si ya no está: cerrar sesión es idempotente. */
    @Transactional
    public void revoke(String raw) {
        repository.findByTokenHash(hash(raw)).ifPresent(token -> {
            token.revoke();
            repository.save(token);
        });
    }

    private RefreshToken require(String raw) {
        return repository.findByTokenHash(hash(raw))
                .filter(token -> token.isUsable(Instant.now()))
                .orElseThrow(() -> ApiException.unauthorized(
                        "invalid_refresh_token", "El refresh token no es válido, ya expiró o fue revocado."));
    }

    /** SHA-256 en hexadecimal del valor del token. */
    static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 es obligatorio en toda JVM; si falta, es un problema de plataforma irrecuperable.
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }
}
