package ar.com.planillero.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Código TOTP con el que se confirma una acción (habilitar o apagar el segundo factor). */
public record TwoFactorCodeRequest(
        @NotBlank(message = "El código es obligatorio") String code) {
}
