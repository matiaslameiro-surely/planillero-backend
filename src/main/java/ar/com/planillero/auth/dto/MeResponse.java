package ar.com.planillero.auth.dto;

import java.util.List;

/** Datos del usuario autenticado, para que el cliente arme la sesión. */
public record MeResponse(String username, List<String> roles, boolean twoFactorEnabled) {
}
