package ar.com.planillero.auth;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.springframework.stereotype.Service;

/**
 * Segundo factor con TOTP (RFC 6238).
 *
 * <p>Genera el secreto, arma la URI {@code otpauth://} que el cliente convierte en QR y verifica los
 * códigos de 6 dígitos. Se tolera un período de desfasaje hacia cada lado para absorber relojes
 * ligeramente corridos.
 */
@Service
public class TotpService {

    private static final String ISSUER = "Planillero";

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final CodeVerifier codeVerifier;

    public TotpService() {
        DefaultCodeVerifier verifier = new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());
        verifier.setAllowedTimePeriodDiscrepancy(1);
        this.codeVerifier = verifier;
    }

    /** Secreto base32 que se muestra una sola vez al habilitar el segundo factor. */
    public String generateSecret() {
        return secretGenerator.generate();
    }

    /** Verifica un código de 6 dígitos contra el secreto del usuario. */
    public boolean verify(String secret, String code) {
        if (secret == null || code == null || !code.matches("\\d{6}")) {
            return false;
        }
        try {
            return codeVerifier.isValidCode(secret, code);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /** URI que las apps autenticadoras entienden y convierten en QR. */
    public String otpauthUri(String secret, String username) {
        String label = URLEncoder.encode(ISSUER + ":" + username, StandardCharsets.UTF_8).replace("+", "%20");
        return "otpauth://totp/" + label
                + "?secret=" + secret
                + "&issuer=" + ISSUER
                + "&algorithm=SHA1&digits=6&period=30";
    }
}
