package ar.com.planillero.evidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ar.com.planillero.evidence.crypto.CryptoService;

class CryptoServiceTest {

    private CryptoService cryptoService;

    @BeforeEach
    void setUp() {
        cryptoService = new CryptoService("test-secret-key-32bytes-for-crypto");
    }

    @Test
    @DisplayName("Cálculo de SHA-256 contra vectores conocidos del NIST")
    void sha256KnownVectors() throws IOException {
        // Vector 1: Cadena vacía
        assertEquals(
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                cryptoService.calculateSha256("".getBytes(StandardCharsets.UTF_8)));

        // Vector 2: "abc"
        assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                cryptoService.calculateSha256("abc".getBytes(StandardCharsets.UTF_8)));

        // Vector 3: "The quick brown fox jumps over the lazy dog"
        String text = "The quick brown fox jumps over the lazy dog";
        assertEquals(
                "d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592",
                cryptoService.calculateSha256(text.getBytes(StandardCharsets.UTF_8)));

        // Streaming con ByteArrayInputStream
        try (ByteArrayInputStream in = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))) {
            assertEquals(
                    "d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592",
                    cryptoService.calculateSha256(in));
        }
    }

    @Test
    @DisplayName("Detección de manipulación de binarios: 1 bit alterado produce hash completamente distinto")
    void sha256TamperDetection() {
        byte[] original = "Contenido pericial confidencial firmado".getBytes(StandardCharsets.UTF_8);
        byte[] tampered = original.clone();
        tampered[tampered.length - 1] ^= 0x01; // flip 1 bit

        String originalHash = cryptoService.calculateSha256(original);
        String tamperedHash = cryptoService.calculateSha256(tampered);

        assertFalse(originalHash.equalsIgnoreCase(tamperedHash));
    }

    @Test
    @DisplayName("Generación y verificación de firma HMAC-SHA256")
    void hmacGenerationAndVerification() {
        String data = "{\"visitId\":\"123\",\"evidence\":\"hash\"}";
        String signature = cryptoService.signHmacSha256(data);

        assertTrue(cryptoService.verifyHmacSha256(data, signature));

        // Verificación con datos alterados debe fallar
        assertFalse(cryptoService.verifyHmacSha256(data + " ", signature));

        // Verificación con clave distinta debe fallar
        assertFalse(cryptoService.verifyHmacSha256(data, signature, "otra-clave-invalida"));
    }
}
