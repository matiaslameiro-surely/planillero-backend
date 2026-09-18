package ar.com.planillero.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;

/**
 * Arma el par de claves RSA y los beans que firman y validan los JWT.
 *
 * <p>La firma es RS256. Si se configuran claves por PEM ({@code app.jwt.private-key} y
 * {@code app.jwt.public-key}) se usan esas: es lo que va a hacer un entorno real, con claves
 * provistas por el entorno y nunca versionadas. Si no hay claves configuradas —el caso del
 * desarrollo local— se genera un par efímero en memoria; los tokens dejan de valer al reiniciar y
 * se avisa por log.
 */
@Configuration
@EnableConfigurationProperties({ JwtProperties.class, AuthProperties.class })
public class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    @Bean
    public RSAKey rsaKey(JwtProperties properties) throws IOException, GeneralSecurityException {
        if (hayClavesConfiguradas(properties)) {
            return new RSAKey.Builder(readPublicKey(properties.publicKey()))
                    .privateKey(readPrivateKey(properties.privateKey()))
                    .keyID("planillero")
                    .build();
        }
        log.warn("No hay claves JWT configuradas (app.jwt.private-key / app.jwt.public-key): se genera un "
                + "par efímero para desarrollo. Los tokens dejan de ser válidos al reiniciar la aplicación.");
        return generateEphemeralKey();
    }

    private static boolean hayClavesConfiguradas(JwtProperties properties) {
        return properties.privateKey() != null && properties.publicKey() != null
                && properties.privateKey().exists() && properties.publicKey().exists();
    }

    private static RSAKey generateEphemeralKey() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey((RSAPrivateKey) pair.getPrivate())
                .keyID("planillero-dev")
                .build();
    }

    @Bean
    public JwtEncoder jwtEncoder(RSAKey rsaKey) {
        JWKSet jwkSet = new JWKSet(rsaKey);
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(jwkSet));
    }

    @Bean
    public JwtDecoder jwtDecoder(RSAKey rsaKey) throws JOSEException {
        return NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
    }

    private static RSAPrivateKey readPrivateKey(Resource resource) throws IOException, GeneralSecurityException {
        byte[] der = decodePem(resource.getContentAsString(StandardCharsets.UTF_8));
        return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static RSAPublicKey readPublicKey(Resource resource) throws IOException, GeneralSecurityException {
        byte[] der = decodePem(resource.getContentAsString(StandardCharsets.UTF_8));
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }

    /** Quita los encabezados/cierre del PEM, los espacios y devuelve el DER en base64. */
    private static byte[] decodePem(String pem) {
        String limpio = pem
                .replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(limpio);
    }
}
