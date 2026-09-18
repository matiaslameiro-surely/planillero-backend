package ar.com.planillero.evidence.dto;

import java.util.List;
import java.util.UUID;

import ar.com.planillero.evidence.model.VerificationStatus;

/**
 * DTO con el resultado detallado de la auditoría y verificación pericial del manifiesto.
 */
public record VerificationResultResponse(
        UUID manifestId,
        UUID visitId,
        VerificationStatus status,
        boolean signatureValid,
        boolean allEvidencesIntact,
        String message,
        List<EvidenceVerificationDetail> evidences) {

    public record EvidenceVerificationDetail(
            UUID evidenceId,
            String sha256Expected,
            String sha256Actual,
            boolean intact,
            String status) {
    }
}
