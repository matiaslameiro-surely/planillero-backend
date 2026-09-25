package ar.com.planillero.evidence.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import ar.com.planillero.evidence.dto.CreateManifestRequest;
import ar.com.planillero.evidence.dto.EvidenceResponse;
import ar.com.planillero.evidence.dto.ManifestResponse;
import ar.com.planillero.evidence.dto.VerificationResultResponse;
import ar.com.planillero.evidence.model.Evidence;
import ar.com.planillero.evidence.model.EvidenceType;
import ar.com.planillero.evidence.service.EvidenceService;
import ar.com.planillero.evidence.service.ManifestService;
import ar.com.planillero.user.User;
import ar.com.planillero.user.UserRepository;
import jakarta.validation.Valid;

/**
 * Controlador REST para ingesta de evidencias periciales, visualización y sellado criptográfico de visitas.
 */
@RestController
@RequestMapping("/api/v1/visits/{visitId}")
public class EvidenceController {

    private final EvidenceService evidenceService;
    private final ManifestService manifestService;
    private final UserRepository userRepository;

    public EvidenceController(
            EvidenceService evidenceService,
            ManifestService manifestService,
            UserRepository userRepository) {
        this.evidenceService = evidenceService;
        this.manifestService = manifestService;
        this.userRepository = userRepository;
    }

    /**
     * Ingesta de archivo pericial (foto o firma) con cálculo concurrente de SHA-256 en streaming.
     */
    @PostMapping(value = "/evidences", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('OPERATOR', 'SUPERVISOR', 'ADMINISTRATOR')")
    public ResponseEntity<EvidenceResponse> uploadEvidence(
            @PathVariable UUID visitId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", required = false, defaultValue = "PHOTO") EvidenceType type,
            @RequestParam(value = "capturedAt", required = false) Instant capturedAt,
            @RequestParam(value = "metadata", required = false) String metadata,
            @RequestHeader(value = "X-Content-SHA256", required = false) String clientHash,
            Authentication authentication) {

        EvidenceResponse response = evidenceService.saveEvidence(
                visitId, file, type, capturedAt, clientHash, metadata, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Consulta el listado de evidencias asociadas a la visita pericial.
     */
    @GetMapping("/evidences")
    @PreAuthorize("hasAnyRole('OPERATOR', 'SUPERVISOR', 'ADMINISTRATOR')")
    public ResponseEntity<List<EvidenceResponse>> getEvidences(
            @PathVariable UUID visitId,
            Authentication authentication) {
        return ResponseEntity.ok(evidenceService.getEvidencesByVisit(visitId, authentication.getName()));
    }

    /**
     * Descarga/visualiza el binario de una evidencia pericial custodiada.
     */
    @GetMapping("/evidences/{evidenceId}/file")
    @PreAuthorize("hasAnyRole('OPERATOR', 'SUPERVISOR', 'ADMINISTRATOR')")
    public ResponseEntity<Resource> getEvidenceFile(
            @PathVariable UUID visitId,
            @PathVariable UUID evidenceId,
            Authentication authentication) {

        Evidence evidence = evidenceService.getEvidenceEntity(visitId, evidenceId, authentication.getName());
        Resource resource = evidenceService.loadEvidenceResource(evidence);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(evidence.getContentType()))
                .contentLength(evidence.getFileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + evidence.getFileName() + "\"")
                .header(HttpHeaders.ETAG, "\"" + evidence.getSha256Hash() + "\"")
                .body(resource);
    }

    /**
     * Generación y sellado criptográfico del manifiesto de visita con firma digital HMAC-SHA256.
     */
    @PostMapping("/manifest")
    @PreAuthorize("hasAnyRole('OPERATOR', 'SUPERVISOR', 'ADMINISTRATOR')")
    public ResponseEntity<ManifestResponse> createManifest(
            @PathVariable UUID visitId,
            @Valid @RequestBody CreateManifestRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {

        UUID userId = resolveUserId(jwt);
        ManifestResponse response = manifestService.createAndSignManifest(
                visitId, userId, request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Consulta el último manifiesto sellado para la visita.
     */
    @GetMapping("/manifest")
    @PreAuthorize("hasAnyRole('OPERATOR', 'SUPERVISOR', 'ADMINISTRATOR')")
    public ResponseEntity<ManifestResponse> getManifest(
            @PathVariable UUID visitId,
            Authentication authentication) {
        return ResponseEntity.ok(manifestService.getLatestManifest(visitId, authentication.getName()));
    }

    /**
     * Auditoría y verificación pericial del manifiesto y los binarios custodiados.
     */
    @PostMapping("/manifest/verify")
    @PreAuthorize("hasAnyRole('OPERATOR', 'SUPERVISOR', 'ADMINISTRATOR')")
    public ResponseEntity<VerificationResultResponse> verifyManifest(
            @PathVariable UUID visitId,
            Authentication authentication) {
        return ResponseEntity.ok(manifestService.verifyManifest(visitId, authentication.getName()));
    }

    private UUID resolveUserId(Jwt jwt) {
        if (jwt != null && jwt.getSubject() != null) {
            return userRepository.findByUsername(jwt.getSubject())
                    .map(User::getId)
                    .orElse(UUID.nameUUIDFromBytes(jwt.getSubject().getBytes()));
        }
        return UUID.randomUUID();
    }
}
