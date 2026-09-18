package ar.com.planillero.evidence.service;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import ar.com.planillero.evidence.dto.EvidenceResponse;
import ar.com.planillero.evidence.model.Evidence;
import ar.com.planillero.evidence.model.EvidenceType;
import ar.com.planillero.evidence.repository.EvidenceRepository;
import ar.com.planillero.evidence.storage.ObjectStorageService;

/**
 * Servicio de negocio para ingesta y gestión de evidencias periciales con cálculo SHA-256 en streaming.
 */
@Service
public class EvidenceService {

    private final EvidenceRepository evidenceRepository;
    private final ObjectStorageService storageService;

    public EvidenceService(
            EvidenceRepository evidenceRepository,
            ObjectStorageService storageService) {
        this.evidenceRepository = evidenceRepository;
        this.storageService = storageService;
    }

    /**
     * Ingesta un archivo de evidencia pericial mediante streaming, calculando el digest SHA-256 concurrente.
     */
    @Transactional
    public EvidenceResponse saveEvidence(
            UUID visitId,
            MultipartFile file,
            EvidenceType evidenceType,
            Instant capturedAt,
            String clientDeclaredHash,
            String metadata) {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo de evidencia no puede ser nulo ni estar vacío");
        }
        if (visitId == null) {
            throw new IllegalArgumentException("El identificador de visita es obligatorio");
        }
        if (evidenceType == null) {
            evidenceType = EvidenceType.PHOTO;
        }

        UUID evidenceId = UUID.randomUUID();
        String extension = extractExtension(file.getOriginalFilename(), file.getContentType());
        // Almacenamiento con clave UUID aleatoria para mitigar OWASP A04
        String objectKey = "visits/" + visitId + "/" + evidenceId + extension;

        MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 no disponible", e);
        }

        String calculatedHash;
        try (InputStream fileIn = file.getInputStream();
             DigestInputStream digestIn = new DigestInputStream(fileIn, messageDigest)) {

            // Transmisión en streaming hacia el storage WORM
            storageService.save(objectKey, digestIn, file.getSize(), file.getContentType());
            calculatedHash = HexFormat.of().formatHex(messageDigest.digest());

        } catch (IOException e) {
            storageService.delete(objectKey);
            throw new IllegalStateException("Error al leer el archivo de evidencia en streaming", e);
        } catch (RuntimeException ex) {
            storageService.delete(objectKey);
            throw ex;
        }

        // Validación de coincidencia si el cliente envió un hash previo
        if (clientDeclaredHash != null && !clientDeclaredHash.trim().isEmpty()) {
            String sanitizedClientHash = clientDeclaredHash.trim().toLowerCase();
            if (!calculatedHash.equalsIgnoreCase(sanitizedClientHash)) {
                storageService.delete(objectKey);
                throw new IntegrityMismatchException(
                        "Discrepancia de integridad: el hash SHA-256 declarado por el cliente (" + sanitizedClientHash
                                + ") no coincide con el calculado por el servidor (" + calculatedHash + ")");
            }
        }

        Evidence evidence = new Evidence(
                evidenceId,
                visitId,
                evidenceType,
                objectKey,
                file.getOriginalFilename(),
                file.getContentType() != null ? file.getContentType() : "application/octet-stream",
                file.getSize(),
                calculatedHash,
                capturedAt != null ? capturedAt : Instant.now(),
                metadata);

        Evidence saved = evidenceRepository.save(evidence);
        return EvidenceResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<EvidenceResponse> getEvidencesByVisit(UUID visitId) {
        return evidenceRepository.findByVisitIdOrderByCapturedAtAsc(visitId).stream()
                .map(EvidenceResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Evidence getEvidenceEntity(UUID visitId, UUID evidenceId) {
        return evidenceRepository.findByIdAndVisitId(evidenceId, visitId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Evidencia no encontrada para la visita indicada: " + evidenceId));
    }

    @Transactional(readOnly = true)
    public Resource loadEvidenceResource(UUID visitId, UUID evidenceId) {
        Evidence evidence = getEvidenceEntity(visitId, evidenceId);
        InputStream stream = storageService.load(evidence.getStoragePath());
        return new InputStreamResource(stream);
    }

    private String extractExtension(String filename, String contentType) {
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf('.'));
        }
        if (contentType != null) {
            if (contentType.contains("jpeg") || contentType.contains("jpg")) return ".jpg";
            if (contentType.contains("png")) return ".png";
            if (contentType.contains("pdf")) return ".pdf";
            if (contentType.contains("svg")) return ".svg";
        }
        return ".bin";
    }
}
