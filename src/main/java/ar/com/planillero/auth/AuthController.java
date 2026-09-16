package ar.com.planillero.auth;

import ar.com.planillero.auth.dto.LoginRequest;
import ar.com.planillero.auth.dto.LoginResponse;
import ar.com.planillero.auth.dto.MeResponse;
import ar.com.planillero.auth.dto.RefreshRequest;
import ar.com.planillero.auth.dto.TokenResponse;
import ar.com.planillero.auth.dto.TwoFactorCodeRequest;
import ar.com.planillero.auth.dto.TwoFactorSetupResponse;
import ar.com.planillero.auth.dto.TwoFactorVerifyRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints de autenticación y gestión del segundo factor. */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** Login. Puede devolver los tokens o pedir el código de segundo factor. */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.username(), request.password());
    }

    /** Completa el segundo factor del login y devuelve los tokens. */
    @PostMapping("/verify-2fa")
    public TokenResponse verifyTwoFactor(@Valid @RequestBody TwoFactorVerifyRequest request) {
        return authService.verifyTwoFactor(request.challengeId(), request.code());
    }

    /** Rota el refresh token: el que llega queda revocado. */
    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    /** Cierra la sesión revocando el refresh token. Es idempotente. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
    }

    /** Datos del usuario autenticado. */
    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        return authService.me(authentication.getName());
    }

    /** Genera el secreto de TOTP; queda pendiente hasta que se confirme con un código. */
    @PostMapping("/2fa/setup")
    public TwoFactorSetupResponse setupTwoFactor(Authentication authentication) {
        return authService.setupTwoFactor(authentication.getName());
    }

    /** Confirma el código del secreto pendiente y enciende el segundo factor. */
    @PostMapping("/2fa/enable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enableTwoFactor(Authentication authentication, @Valid @RequestBody TwoFactorCodeRequest request) {
        authService.enableTwoFactor(authentication.getName(), request.code());
    }

    /** Apaga el segundo factor pidiendo un código válido. */
    @PostMapping("/2fa/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disableTwoFactor(Authentication authentication, @Valid @RequestBody TwoFactorCodeRequest request) {
        authService.disableTwoFactor(authentication.getName(), request.code());
    }
}
