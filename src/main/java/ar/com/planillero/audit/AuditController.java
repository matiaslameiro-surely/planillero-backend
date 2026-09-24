package ar.com.planillero.audit;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ar.com.planillero.audit.dto.AuditLogDto;
import ar.com.planillero.audit.dto.ChainVerificationResponse;
import ar.com.planillero.common.ApiException;
import ar.com.planillero.planning.Visit;
import ar.com.planillero.planning.VisitRepository;

/**
 * Consulta de auditoría y verificación de la cadena de custodia. Los dos endpoints son exclusivos
 * de {@code ADMINISTRATOR}: es el único rol de ese nivel que existe hoy (ver {@code 01-spec.md}).
 */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private static final String VISIT = "VISIT";

    private final AuditLogRepository repository;
    private final AuditChainService chainService;
    private final VisitRepository visitRepository;

    public AuditController(AuditLogRepository repository, AuditChainService chainService,
            VisitRepository visitRepository) {
        this.repository = repository;
        this.chainService = chainService;
        this.visitRepository = visitRepository;
    }

    /** Página de eventos de auditoría, más recientes primero, con filtros opcionales. */
    @GetMapping("/logs")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public Page<AuditLogDto> logs(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // El orden ya lo fija el @Query de AuditLogRepository.search: el Pageable sólo pagina.
        Page<AuditLogEntry> result = repository.search(eventType, username, from, to, PageRequest.of(page, size));
        Map<String, String> codes = visitCodes(result.getContent());
        return result.map((entry) -> AuditLogDto.from(entry, VISIT.equals(entry.getEntityType())
                ? codes.get(entry.getEntityId())
                : null));
    }

    /** Verifica la cadena de hashes completa, o el segmento de una visita puntual. */
    @GetMapping("/verify")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ChainVerificationResponse verify(@RequestParam(required = false) String visitId) {
        String entityId = resolveVisitId(visitId);
        return ChainVerificationResponse.from(chainService.verify(entityId));
    }

    /**
     * Código de visita de cada fila {@code VISIT} de la página, con una sola consulta por clave
     * primaria (no una por fila). Los {@code entityId} que no son UUID se ignoran.
     */
    private Map<String, String> visitCodes(List<AuditLogEntry> entries) {
        List<UUID> ids = entries.stream()
                .filter((entry) -> VISIT.equals(entry.getEntityType()))
                .map((entry) -> parseUuid(entry.getEntityId()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return visitRepository.findAllById(ids).stream()
                .collect(Collectors.toMap((visit) -> visit.getId().toString(), Visit::getCode, (a, b) -> a));
    }

    /**
     * Vacío verifica la cadena completa. Si no, acepta el UUID de la visita o su código
     * ({@code V-1001}), que es lo que conoce el supervisor.
     */
    private String resolveVisitId(String visitId) {
        if (visitId == null || visitId.isBlank()) {
            return null;
        }
        String value = visitId.trim();
        UUID uuid = parseUuid(value);
        if (uuid != null) {
            return uuid.toString();
        }
        return visitRepository.findByCode(value)
                .map((visit) -> visit.getId().toString())
                .orElseThrow(() -> ApiException.notFound("visit_not_found",
                        "No existe una visita con ese ID o código."));
    }

    private static UUID parseUuid(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
