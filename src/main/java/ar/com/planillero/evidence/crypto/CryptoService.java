package ar.com.planillero.evidence.crypto;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Servicio criptográfico para cálculo de resúmenes SHA-256 y firmas HMAC-SHA256.
 *
 * <p>Asegura la cadena de custodia y la verificación de integridad de evidencias periciales
 * y manifiestos de visita.
 */
@Service
public class CryptoService {

    private static final String SHA_256 = "SHA-256";
    private static final String HMAC_SHA_256 = "HmacSHA256";

    private final String hmacSecret;

    public CryptoService(@Value("${app.crypto.hmac-secret}") String hmacSecret) {
        if (hmacSecret == null || hmacSecret.trim().isEmpty()) {
            throw new IllegalArgumentException("La clave app.crypto.hmac-secret no puede ser nula ni vacía");
        }
        this.hmacSecret = hmacSecret;
    }

    /**
     * Calcula el digest SHA-256 en formato hexadecimal en minúsculas sobre un array de bytes.
     */
    public String calculateSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 no disponible en la JVM", e);
        }
    }

    /**
     * Calcula el digest SHA-256 en formato hexadecimal en minúsculas leyendo un stream de datos.
     */
    public String calculateSha256(InputStream inputStream) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 no disponible en la JVM", e);
        }
    }

    /**
     * Firma una cadena de texto canónica utilizando HMAC-SHA256 y la clave secreta configurada.
     */
    public String signHmacSha256(String data) {
        return signHmacSha256(data, this.hmacSecret);
    }

    /**
     * Firma una cadena de texto utilizando HMAC-SHA256 con una clave específica.
     */
    public String signHmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA_256);
            mac.init(secretKey);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(rawHmac);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Error al computar firma HMAC-SHA256", e);
        }
    }

    /**
     * Verifica la firma HMAC-SHA256 en tiempo constante para mitigar ataques de temporización (timing attacks).
     */
    public boolean verifyHmacSha256(String data, String signature) {
        return verifyHmacSha256(data, signature, this.hmacSecret);
    }

    /**
     * Verifica la firma HMAC-SHA256 con clave explícita en tiempo constante.
     */
    public boolean verifyHmacSha256(String data, String expectedSignature, String secret) {
        if (data == null || expectedSignature == null || secret == null) {
            return false;
        }
        String calculated = signHmacSha256(data, secret);
        byte[] expectedBytes = expectedSignature.toLowerCase().getBytes(StandardCharsets.UTF_8);
        byte[] calculatedBytes = calculated.toLowerCase().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, calculatedBytes);
    }
}
