package ar.com.planillero.auth.dto;

/**
 * Secreto de TOTP recién generado.
 *
 * @param secret     secreto base32, para cargar a mano en el autenticador
 * @param otpauthUri URI {@code otpauth://} que el cliente convierte en QR
 */
public record TwoFactorSetupResponse(String secret, String otpauthUri) {
}
