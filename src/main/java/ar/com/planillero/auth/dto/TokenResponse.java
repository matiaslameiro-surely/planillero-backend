package ar.com.planillero.auth.dto;

/** Par de tokens que el cliente usa para autenticarse. */
public record TokenResponse(String accessToken, String refreshToken) {
}
