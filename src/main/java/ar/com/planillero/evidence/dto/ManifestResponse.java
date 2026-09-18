package ar.com.planillero.evidence.dto;

import java.time.Instant;
import java.util.UUID;

import ar.com.planillero.evidence.model.VerificationStatus;
import ar.com.planillero.evidence.model.VisitManifest;

/**
 * DTO que representa un manifiesto de visita pericial firmado.
 */
public record ManifestResponse(
        UUID id,
        UUID visitId,
        UUID userId,
        String deviceInfo,
        String manifestData,
        String hmacSignature,
        VerificationStatus verificationStatus,
        Instant createdAt) {

    public static ManifestResponse from(VisitManifest manifest) {
        return new ManifestResponse(
                manifest.getId(),
                manifest.getVisitId(),
                manifest.getUserId(),
                manifest.getDeviceInfo(),
                manifest.getManifestData(),
                manifest.getHmacSignature(),
                manifest.getVerificationStatus(),
                manifest.getCreatedAt());
    }
}
