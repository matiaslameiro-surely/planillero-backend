package ar.com.planillero.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Verifica el hashing de contraseñas: bcrypt con factor 12 y verificación correcta. */
class PasswordEncoderTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    @Test
    @DisplayName("el hash usa bcrypt con factor 12 y valida la contraseña original")
    void hasheaConFactorDoce() {
        String hash = encoder.encode("Operador123!");

        assertThat(hash).startsWith("$2a$12$");
        assertThat(encoder.matches("Operador123!", hash)).isTrue();
    }

    @Test
    @DisplayName("una contraseña distinta no valida contra el hash")
    void contrasenaDistintaNoValida() {
        String hash = encoder.encode("Operador123!");

        assertThat(encoder.matches("OtraClave!", hash)).isFalse();
    }

    @Test
    @DisplayName("el mismo texto produce hashes distintos (sal aleatoria)")
    void hashesDiferentesPorSal() {
        String primero = encoder.encode("Operador123!");
        String segundo = encoder.encode("Operador123!");

        assertThat(primero).isNotEqualTo(segundo);
        assertThat(encoder.matches("Operador123!", segundo)).isTrue();
    }
}
