package ar.com.planillero.evidence.dto;

import java.time.Instant;
import java.util.UUID;

import ar.com.planillero.evidence.model.Evidence;
import ar.com.planillero.evidence.model.EvidenceType;

/**
 * DTO que representa una evidencia pericial registrada.
 */
public record EvidenceResponse(
        UUID id,
        UUID visitId,
        EvidenceType evidenceType,
        String fileName,
        String contentType,
        long fileSize,
        String sha256Hash,
        Instant capturedAt,
        Instant createdAt,
        String metadata) {

    public static EvidenceResponse from(Evidence evidence) {
        return new EvidenceResponse(
                evidence.getId(),
                evidence.getVisitId(),
                evidence.getEvidenceType(),
                evidence.getFileName(),
                evidence.getContentType(),
                evidence.getFileSize(),
                evidence.getSha256Hash(),
                evidence.getCapturedAt(),
                evidence.getCreatedAt(),
                evidence.getMetadata());
    }
}
