package ar.com.planillero.planning;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.com.planillero.audit.AuditLog;
import ar.com.planillero.common.ApiException;
import ar.com.planillero.planning.dto.CompleteVisitResponse;

/**
 * Servicio para completar una visita pericial en curso.
 */
@Service
public class VisitCompleteService {

    private final VisitRepository visitRepository;
    private final VisitAccessGuard visitAccessGuard;
    private final Clock clock;

    public VisitCompleteService(VisitRepository visitRepository, VisitAccessGuard visitAccessGuard, Clock clock) {
        this.visitRepository = visitRepository;
        this.visitAccessGuard = visitAccessGuard;
        this.clock = clock;
    }

    /**
     * Finaliza la visita pericial si pertenece al operador y se encuentra en estado {@code IN_PROGRESS}.
     *
     * @param visitId  identificador de la visita
     * @param username nombre de usuario autenticado
     * @return confirmación de finalización de la visita
     */
    @Transactional
    @AuditLog(eventType = "VISIT_COMPLETED", entityType = "VISIT")
    public CompleteVisitResponse complete(UUID visitId, String username) {
        visitAccessGuard.requireAccess(visitId, username);

        Visit visit = visitRepository.findByIdForUpdate(visitId)
                .orElseThrow(() -> ApiException.notFound("visit_not_found", "No existe la visita indicada."));

        if (visit.getStatus() == VisitStatus.COMPLETED) {
            throw ApiException.conflict("visit_already_completed",
                    "La visita " + visit.getCode() + " ya está completada.");
        }

        if (visit.getStatus() != VisitStatus.IN_PROGRESS) {
            throw ApiException.conflict("visit_not_in_progress",
                    "Sólo se puede completar una visita en curso (estado "
                            + visit.getStatus().name().toLowerCase() + ").");
        }

        visit.complete();
        visitRepository.save(visit);

        Instant now = clock.instant();
        return new CompleteVisitResponse(visit.getId(), visit.getStatus(), visit.getCode(), now);
    }
}
