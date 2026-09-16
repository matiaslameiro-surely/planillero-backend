package ar.com.planillero.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Código del segundo factor junto al desafío que lo acompaña. */
public record TwoFactorVerifyRequest(
        @NotBlank(message = "El desafío es obligatorio") String challengeId,
        @NotBlank(message = "El código es obligatorio") String code) {
}
