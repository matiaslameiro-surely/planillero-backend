package ar.com.planillero.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

/**
 * Configuración del emisor propio de JWT.
 *
 * @param issuer             valor del claim {@code iss}
 * @param accessTokenTtl     vida del access token
 * @param refreshTokenTtl    vida del refresh token
 * @param challengeTokenTtl  vida del token de desafío del segundo factor
 * @param privateKey         clave privada RSA (PKCS#8) con la que se firma
 * @param publicKey          clave pública RSA (X.509) con la que se valida
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String issuer,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        Duration challengeTokenTtl,
        Resource privateKey,
        Resource publicKey) {
}
