package ar.com.planillero.audit;

import java.time.Instant;
import java.util.UUID;

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

/**
 * Consulta de auditoría y verificación de la cadena de custodia. Los dos endpoints son exclusivos
 * de {@code ADMINISTRATOR}: es el único rol de ese nivel que existe hoy (ver {@code 01-spec.md}).
 */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final AuditLogRepository repository;
    private final AuditChainService chainService;

    public AuditController(AuditLogRepository repository, AuditChainService chainService) {
        this.repository = repository;
        this.chainService = chainService;
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
        return result.map(AuditLogDto::from);
    }

    /** Verifica la cadena de hashes completa, o el segmento de una visita puntual. */
    @GetMapping("/verify")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ChainVerificationResponse verify(@RequestParam(required = false) UUID visitId) {
        String entityId = visitId != null ? visitId.toString() : null;
        return ChainVerificationResponse.from(chainService.verify(entityId));
    }
}
