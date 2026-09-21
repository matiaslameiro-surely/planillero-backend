package ar.com.planillero.audit.dto;

import java.util.UUID;

import ar.com.planillero.audit.AuditChainService.ChainVerificationResult;

/** Resultado de auditar la integridad de la cadena de hashes, completa o de una visita. */
public record ChainVerificationResponse(boolean intacta, UUID primerEslabonRotoId, String motivo) {

    public static ChainVerificationResponse from(ChainVerificationResult result) {
        return new ChainVerificationResponse(
                result.ok(),
                result.brokenAt().orElse(null),
                result.reason().orElse(null));
    }
}
