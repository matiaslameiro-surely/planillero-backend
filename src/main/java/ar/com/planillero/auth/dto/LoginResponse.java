package ar.com.planillero.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Resultado del login.
 *
 * <p>Puede ser una autenticación completa (con tokens) o un pedido de segundo factor (con el
 * desafío). Los campos que no aplican al caso van en {@code null} y no se serializan.
 *
 * @param twoFactorRequired indica si el usuario debe completar el segundo factor
 * @param challengeId       desafío a enviar en {@code /auth/verify-2fa}; sólo si hace falta el 2FA
 * @param accessToken       token de acceso; sólo si el login terminó
 * @param refreshToken      token para renovar la sesión; sólo si el login terminó
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponse(
        boolean twoFactorRequired,
        String challengeId,
        String accessToken,
        String refreshToken) {

    public static LoginResponse authenticated(TokenResponse tokens) {
        return new LoginResponse(false, null, tokens.accessToken(), tokens.refreshToken());
    }

    public static LoginResponse twoFactorRequired(String challengeId) {
        return new LoginResponse(true, challengeId, null, null);
    }
}
