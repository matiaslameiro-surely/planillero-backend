package ar.com.planillero.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Cuerpo con el refresh token, para rotarlo o revocarlo. */
public record RefreshRequest(
        @NotBlank(message = "El refresh token es obligatorio") String refreshToken) {
}
