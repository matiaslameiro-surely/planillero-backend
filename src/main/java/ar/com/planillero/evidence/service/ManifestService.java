package ar.com.planillero.evidence.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import ar.com.planillero.audit.AuditLog;
import ar.com.planillero.evidence.crypto.CryptoService;
import ar.com.planillero.evidence.dto.CreateManifestRequest;
import ar.com.planillero.evidence.dto.ManifestResponse;
import ar.com.planillero.evidence.dto.VerificationResultResponse;
import ar.com.planillero.evidence.dto.VerificationResultResponse.EvidenceVerificationDetail;
import ar.com.planillero.evidence.model.Evidence;
import ar.com.planillero.evidence.model.VerificationStatus;
import ar.com.planillero.evidence.model.VisitManifest;
import ar.com.planillero.evidence.repository.EvidenceRepository;
import ar.com.planillero.evidence.repository.VisitManifestRepository;
import ar.com.planillero.evidence.storage.ObjectStorageService;

/**
 * Servicio para generación, sellado con firma digital HMAC y verificación pericial de manifiestos.
 */
@Service
public class ManifestService {

    private final VisitManifestRepository manifestRepository;
    private final EvidenceRepository evidenceRepository;
    private final CryptoService cryptoService;
    private final ObjectStorageService storageService;
    private final ObjectMapper objectMapper;

    public ManifestService(
            VisitManifestRepository manifestRepository,
            EvidenceRepository evidenceRepository,
            CryptoService cryptoService,
            ObjectStorageService storageService) {
        this.manifestRepository = manifestRepository;
        this.evidenceRepository = evidenceRepository;
        this.cryptoService = cryptoService;
        this.storageService = storageService;
        this.objectMapper = new ObjectMapper().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    /**
     * Genera el manifiesto canónico normalizado, computa la firma HMAC-SHA256 y lo persiste.
     */
    @Transactional
    @AuditLog(eventType = "MANIFEST_SIGNED", entityType = "VISIT")
    public ManifestResponse createAndSignManifest(UUID visitId, UUID userId, CreateManifestRequest request) {
        if (visitId == null) {
            throw new IllegalArgumentException("El identificador de visita es obligatorio");
        }
        if (userId == null) {
            throw new IllegalArgumentException("El identificador de usuario firmante es obligatorio");
        }
        if (request.evidenceIds() == null || request.evidenceIds().isEmpty()) {
            throw new IllegalArgumentException("El manifiesto requiere al menos una evidencia asociada");
        }

        List<Evidence> evidences = new ArrayList<>();
        for (UUID evidenceId : request.evidenceIds()) {
            Evidence ev = evidenceRepository.findByIdAndVisitId(evidenceId, visitId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "La evidencia con ID " + evidenceId + " no pertenece a la visita " + visitId));
            evidences.add(ev);
        }

        // Ordenamiento canónico determinístico por UUID
        evidences.sort(Comparator.comparing(e -> e.getId().toString()));

        List<Map<String, Object>> canonicalEvidences = new ArrayList<>();
        for (Evidence e : evidences) {
            Map<String, Object> item = new java.util.TreeMap<>();
            item.put("fileSize", e.getFileSize());
            item.put("id", e.getId().toString());
            item.put("sha256", e.getSha256Hash());
            item.put("type", e.getEvidenceType().name());
            canonicalEvidences.add(item);
        }

        Map<String, Object> canonicalPayload = new java.util.TreeMap<>();
        canonicalPayload.put("deviceInfo", request.deviceInfo() != null ? request.deviceInfo() : "unknown");
        canonicalPayload.put("evidences", canonicalEvidences);
        canonicalPayload.put("userId", userId.toString());
        canonicalPayload.put("visitId", visitId.toString());

        String canonicalJson;
        try {
            canonicalJson = objectMapper.writeValueAsString(canonicalPayload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Error al serializar el manifiesto canónico", e);
        }

        // Computación de firma HMAC-SHA256
        String hmacSignature = cryptoService.signHmacSha256(canonicalJson);

        VisitManifest manifest = new VisitManifest(
                UUID.randomUUID(),
                visitId,
                userId,
                request.deviceInfo(),
                canonicalJson,
                hmacSignature,
                VerificationStatus.VERIFIED);

        VisitManifest saved = manifestRepository.save(manifest);
        return ManifestResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public ManifestResponse getLatestManifest(UUID visitId) {
        VisitManifest manifest = manifestRepository.findFirstByVisitIdOrderByCreatedAtDesc(visitId)
                .orElseThrow(() -> new IllegalArgumentException("No existe manifiesto registrado para la visita: " + visitId));
        return ManifestResponse.from(manifest);
    }

    /**
     * Realiza una auditoría criptográfica profunda del manifiesto:
     * 1. Verifica la firma HMAC-SHA256 del contenido canónico.
     * 2. Recalcula el digest SHA-256 de cada binario pericial en el storage para verificar no alteración.
     */
    @Transactional
    public VerificationResultResponse verifyManifest(UUID visitId) {
        VisitManifest manifest = manifestRepository.findFirstByVisitIdOrderByCreatedAtDesc(visitId)
                .orElseThrow(() -> new IllegalArgumentException("No existe manifiesto registrado para la visita: " + visitId));

        boolean signatureValid;
        boolean allEvidencesIntact = true;
        List<EvidenceVerificationDetail> details = new ArrayList<>();

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> rawPayload = objectMapper.readValue(manifest.getManifestData(), Map.class);
            Map<String, Object> canonicalPayload = toCanonicalMap(rawPayload);
            String canonicalJson = objectMapper.writeValueAsString(canonicalPayload);

            signatureValid = cryptoService.verifyHmacSha256(canonicalJson, manifest.getHmacSignature());

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> evidencesList = (List<Map<String, Object>>) canonicalPayload.get("evidences");

            if (evidencesList != null) {
                for (Map<String, Object> item : evidencesList) {
                    UUID evId = UUID.fromString(String.valueOf(item.get("id")));
                    String expectedHash = String.valueOf(item.get("sha256"));

                    Evidence evidenceEntity = evidenceRepository.findByIdAndVisitId(evId, visitId).orElse(null);
                    if (evidenceEntity == null) {
                        allEvidencesIntact = false;
                        details.add(new EvidenceVerificationDetail(evId, expectedHash, "NOT_FOUND_IN_DB", false, "MISSING_RECORD"));
                        continue;
                    }

                    if (!storageService.exists(evidenceEntity.getStoragePath())) {
                        allEvidencesIntact = false;
                        details.add(new EvidenceVerificationDetail(evId, expectedHash, "NOT_FOUND_IN_STORAGE", false, "MISSING_FILE"));
                        continue;
                    }

                    String actualStorageHash;
                    try (InputStream stream = storageService.load(evidenceEntity.getStoragePath())) {
                        actualStorageHash = cryptoService.calculateSha256(stream);
                    } catch (IOException e) {
                        actualStorageHash = "READ_ERROR";
                    }

                    boolean matches = expectedHash.equalsIgnoreCase(actualStorageHash);
                    if (!matches) {
                        allEvidencesIntact = false;
                    }

                    details.add(new EvidenceVerificationDetail(
                            evId,
                            expectedHash,
                            actualStorageHash,
                            matches,
                            matches ? "INTACT" : "TAMPERED"
                    ));
                }
            }
        } catch (Exception e) {
            signatureValid = false;
            allEvidencesIntact = false;
        }

        VerificationStatus finalStatus = (signatureValid && allEvidencesIntact)
                ? VerificationStatus.VERIFIED
                : VerificationStatus.TAMPERED;

        if (manifest.getVerificationStatus() != finalStatus) {
            manifest.setVerificationStatus(finalStatus);
            manifestRepository.save(manifest);
        }

        String message = finalStatus == VerificationStatus.VERIFIED
                ? "Manifiesto y evidencias periciales verificadas íntegramente (HMAC y SHA-256 válidos)"
                : "Alerta pericial: Se detectó alteración en la firma o en los binarios de evidencia (TAMPERED)";

        return new VerificationResultResponse(
                manifest.getId(),
                visitId,
                finalStatus,
                signatureValid,
                allEvidencesIntact,
                message,
                details);
    }

    @SuppressWarnings("unchecked")
    private java.util.Map<String, Object> toCanonicalMap(java.util.Map<String, Object> input) {
        java.util.Map<String, Object> result = new java.util.TreeMap<>();
        for (java.util.Map.Entry<String, Object> entry : input.entrySet()) {
            if ("evidences".equals(entry.getKey()) && entry.getValue() instanceof List<?> list) {
                List<java.util.Map<String, Object>> canonicalList = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof java.util.Map<?, ?> mapItem) {
                        java.util.Map<String, Object> canonicalItem = new java.util.TreeMap<>();
                        mapItem.forEach((k, v) -> {
                            if ("fileSize".equals(String.valueOf(k))) {
                                canonicalItem.put(String.valueOf(k), ((Number) v).longValue());
                            } else {
                                canonicalItem.put(String.valueOf(k), v);
                            }
                        });
                        canonicalList.add(canonicalItem);
                    }
                }
                canonicalList.sort(Comparator.comparing(m -> String.valueOf(m.get("id"))));
                result.put(entry.getKey(), canonicalList);
            } else {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }
}
