package ar.com.planillero.evidence.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;

/**
 * Solicitud de generación y sellado criptográfico del manifiesto de visita.
 */
public record CreateManifestRequest(
        String deviceInfo,
        @NotEmpty(message = "La lista de evidencias no puede estar vacía para sellar el manifiesto")
        List<UUID> evidenceIds) {
}
