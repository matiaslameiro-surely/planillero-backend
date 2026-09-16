package ar.com.planillero.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Credenciales de login. */
public record LoginRequest(
        @NotBlank(message = "El usuario es obligatorio") String username,
        @NotBlank(message = "La contraseña es obligatoria") String password) {
}
