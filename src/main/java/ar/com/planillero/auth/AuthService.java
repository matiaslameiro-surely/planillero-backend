package ar.com.planillero.auth;

import java.util.List;

import ar.com.planillero.auth.dto.LoginResponse;
import ar.com.planillero.auth.dto.MeResponse;
import ar.com.planillero.auth.dto.TokenResponse;
import ar.com.planillero.auth.dto.TwoFactorSetupResponse;
import ar.com.planillero.common.ApiException;
import ar.com.planillero.user.User;
import ar.com.planillero.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquesta el ciclo de autenticación: login, segundo factor, refresh, cierre de sesión y gestión del
 * TOTP.
 *
 * <p>El login no distingue "usuario inexistente" de "contraseña incorrecta": siempre responde lo
 * mismo para no filtrar qué usuarios existen.
 */
@Service
public class AuthService {

    /**
     * Hash de descarte.
     *
     * <p>Cuando el usuario no existe igual se ejecuta un {@code matches} contra este hash para que
     * el tiempo de respuesta no delate su existencia.
     */
    private static final String DUMMY_HASH =
            "$2a$12$cN6/yxSkIISqlYD798skYuUJDoPhu5zJlCjzYDex66HTwIB6IF/MG";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    private final TotpService totpService;
    private final LoginAttemptService loginAttemptService;
    private final TwoFactorChallengeStore challengeStore;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            RefreshTokenService refreshTokenService,
            TotpService totpService,
            LoginAttemptService loginAttemptService,
            TwoFactorChallengeStore challengeStore) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.refreshTokenService = refreshTokenService;
        this.totpService = totpService;
        this.loginAttemptService = loginAttemptService;
        this.challengeStore = challengeStore;
    }

    @Transactional
    public LoginResponse login(String username, String password) {
        if (loginAttemptService.isBlocked(username)) {
            throw ApiException.tooManyRequests(
                    "too_many_attempts", "Demasiados intentos fallidos. Probá de nuevo más tarde.");
        }

        User user = userRepository.findByUsername(username).orElse(null);
        boolean matches;
        if (user != null) {
            matches = passwordEncoder.matches(password, user.getPasswordHash());
        } else {
            // El usuario no existe: igual pagamos el costo de un hash para no delatarlo por tiempo.
            passwordEncoder.matches(password, DUMMY_HASH);
            matches = false;
        }

        if (!matches || !user.isEnabled()) {
            loginAttemptService.registerFailure(username);
            throw ApiException.unauthorized("invalid_credentials", "Usuario o contraseña incorrectos.");
        }

        loginAttemptService.reset(username);

        if (user.isTwoFactorEnabled()) {
            return LoginResponse.twoFactorRequired(tokenService.issueTwoFactorChallenge(user));
        }
        return LoginResponse.authenticated(issueTokens(user));
    }

    /**
     * Completa el segundo factor del login.
     *
     * <p>El código se cuenta igual que la contraseña: superado el umbral de intentos fallidos el
     * desafío queda frenado con 429, y un desafío ya canjeado no vuelve a servir.
     */
    @Transactional
    public TokenResponse verifyTwoFactor(String challengeId, String code) {
        TwoFactorChallenge challenge = tokenService.readTwoFactorChallenge(challengeId);
        String username = challenge.username();

        if (loginAttemptService.isBlocked(twoFactorKey(username))) {
            throw ApiException.tooManyRequests(
                    "too_many_attempts", "Demasiados intentos fallidos. Probá de nuevo más tarde.");
        }

        User user = requireUser(username);
        if (!user.isTwoFactorEnabled() || !totpService.verify(user.getTwoFactorSecret(), code)) {
            loginAttemptService.registerFailure(twoFactorKey(username));
            throw ApiException.unauthorized("invalid_two_factor_code", "El código de verificación es incorrecto.");
        }

        if (!challengeStore.tryConsume(challenge.id())) {
            throw ApiException.unauthorized("invalid_challenge", "El desafío de segundo factor ya fue usado.");
        }

        loginAttemptService.reset(twoFactorKey(username));
        return issueTokens(user);
    }

    @Transactional
    public TokenResponse refresh(String refreshToken) {
        User user = refreshTokenService.consume(refreshToken);
        if (!user.isEnabled()) {
            throw ApiException.unauthorized("invalid_refresh_token", "El refresh token no es válido.");
        }
        return issueTokens(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    @Transactional(readOnly = true)
    public MeResponse me(String username) {
        User user = requireUser(username);
        return new MeResponse(user.getUsername(), roleNames(user), user.isTwoFactorEnabled());
    }

    @Transactional
    public TwoFactorSetupResponse setupTwoFactor(String username) {
        User user = requireUser(username);
        if (user.isTwoFactorEnabled()) {
            throw ApiException.conflict("two_factor_already_enabled",
                    "El segundo factor ya está habilitado. Deshabilitálo antes de generar un secreto nuevo.");
        }
        String secret = totpService.generateSecret();
        user.setPendingTwoFactorSecret(secret);
        userRepository.save(user);
        return new TwoFactorSetupResponse(secret, totpService.otpauthUri(secret, username));
    }

    @Transactional
    public void enableTwoFactor(String username, String code) {
        User user = requireUser(username);
        if (!totpService.verify(user.getTwoFactorSecret(), code)) {
            throw ApiException.badRequest(
                    "invalid_two_factor_code", "El código no coincide con el secreto pendiente.");
        }
        user.enableTwoFactor();
        userRepository.save(user);
    }

    @Transactional
    public void disableTwoFactor(String username, String code) {
        User user = requireUser(username);
        if (!user.isTwoFactorEnabled()) {
            return;
        }
        if (!totpService.verify(user.getTwoFactorSecret(), code)) {
            throw ApiException.badRequest("invalid_two_factor_code", "El código de verificación es incorrecto.");
        }
        user.disableTwoFactor();
        userRepository.save(user);
    }

    private TokenResponse issueTokens(User user) {
        return new TokenResponse(tokenService.issueAccessToken(user), refreshTokenService.issue(user));
    }

    private User requireUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> ApiException.unauthorized("invalid_credentials", "El usuario no existe."));
    }

    /** Clave propia del control de intentos del segundo factor, separada de la del login. */
    private static String twoFactorKey(String username) {
        return "2fa:" + username;
    }

    private static List<String> roleNames(User user) {
        return user.getRoles().stream().map(role -> role.getName().name()).toList();
    }
}
