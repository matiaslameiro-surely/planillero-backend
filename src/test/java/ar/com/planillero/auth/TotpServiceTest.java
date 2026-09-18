package ar.com.planillero.auth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.samstevens.totp.code.DefaultCodeGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Verifica la generación y la validación de códigos TOTP. */
class TotpServiceTest {

    private final TotpService totpService = new TotpService();

    @Test
    @DisplayName("un código generado con el secreto es válido")
    void codigoGeneradoEsValido() throws Exception {
        String secret = totpService.generateSecret();
        String code = currentCode(secret);

        assertThat(totpService.verify(secret, code)).isTrue();
    }

    @Test
    @DisplayName("un código que no corresponde al secreto es rechazado")
    void codigoAjenoEsRechazado() {
        String secret = totpService.generateSecret();

        assertThat(totpService.verify(secret, "000000")).isFalse();
    }

    @Test
    @DisplayName("un código con formato inválido es rechazado sin excepción")
    void codigoConFormatoInvalidoEsRechazado() {
        String secret = totpService.generateSecret();

        assertThat(totpService.verify(secret, "abc")).isFalse();
        assertThat(totpService.verify(secret, null)).isFalse();
        assertThat(totpService.verify(null, "123456")).isFalse();
    }

    @Test
    @DisplayName("la URI otpauth incluye el secreto y el emisor")
    void uriOtpauthIncluyeSecretoYEmisor() {
        String secret = totpService.generateSecret();

        String uri = totpService.otpauthUri(secret, "operador.demo");

        assertThat(uri).startsWith("otpauth://totp/");
        assertThat(uri).contains("secret=" + secret);
        assertThat(uri).contains("issuer=Planillero");
        assertThat(uri).contains("operador.demo");
    }

    /** Genera el código vigente con la misma ventana de 30 segundos de TOTP. */
    private static String currentCode(String secret) throws Exception {
        long counter = java.time.Instant.now().getEpochSecond() / 30;
        return new DefaultCodeGenerator().generate(secret, counter);
    }
}
