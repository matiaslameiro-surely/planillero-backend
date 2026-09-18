package ar.com.planillero.auth;

/**
 * Desafío de segundo factor ya validado como token.
 *
 * @param username usuario al que pertenece el desafío
 * @param id       identificador del token ({@code jti}), con el que se marca como usado
 */
public record TwoFactorChallenge(String username, String id) {
}
