package ar.com.planillero.planning;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import ar.com.planillero.common.ApiException;
import ar.com.planillero.planning.dto.StartVisitRequest;
import ar.com.planillero.planning.dto.StartVisitResponse;
import ar.com.planillero.user.User;
import ar.com.planillero.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inicio de una visita: acredita la presencia del operador con su ubicación y la doble referencia
 * temporal (hora del dispositivo y hora del servidor).
 *
 * <p>El {@code @PreAuthorize} del controlador asegura el rol; este service asegura que la visita esté
 * en una hoja de ruta del propio operador, que es el control de acceso horizontal (OWASP A01).
 */
@Service
public class VisitStartService {

    private final UserRepository userRepository;
    private final VisitRepository visitRepository;
    private final RouteSheetRepository routeSheetRepository;
    private final Clock clock;

    public VisitStartService(UserRepository userRepository, VisitRepository visitRepository,
            RouteSheetRepository routeSheetRepository, Clock clock) {
        this.userRepository = userRepository;
        this.visitRepository = visitRepository;
        this.routeSheetRepository = routeSheetRepository;
        this.clock = clock;
    }

    /**
     * Inicia la visita, siempre que esté asignada al operador y en estado {@code ASSIGNED}.
     *
     * <p>Se comprueba la asignación antes de bloquear la fila: un operador que no tiene nada que ver
     * con la visita no debería poder hacer esperar a otro. Después se lee la visita con bloqueo y
     * recién entonces se valida el estado, para que dos inicios simultáneos no se pisen.
     */
    @Transactional
    public StartVisitResponse start(UUID visitId, StartVisitRequest request, String username) {
        User operator = userRepository.findByUsername(username)
                .orElseThrow(() -> ApiException.unauthorized("unauthorized", "Sesión inválida."));

        if (!routeSheetRepository.existsByOperatorIdAndVisitId(operator.getId(), visitId)) {
            if (!visitRepository.existsById(visitId)) {
                throw ApiException.notFound("visit_not_found", "La visita no existe.");
            }
            throw ApiException.forbidden("visit_not_assigned",
                    "La visita no está asignada a este operador.");
        }

        Visit visit = visitRepository.findByIdForUpdate(visitId)
                .orElseThrow(() -> ApiException.notFound("visit_not_found", "La visita no existe."));

        if (visit.getStatus() != VisitStatus.ASSIGNED) {
            throw ApiException.conflict("visit_not_startable",
                    "La visita " + visit.getCode() + " no se puede iniciar (estado "
                            + visit.getStatus().name().toLowerCase() + ").");
        }

        Instant serverTime = clock.instant();
        long drift = DriftCalculator.driftSeconds(request.clientTimestamp(), serverTime);
        visit.start(request.latitude(), request.longitude(), request.accuracyMeters(),
                request.clientTimestamp(), serverTime, drift, operator.getId());

        return new StartVisitResponse(visit.getId(), visit.getStatus(), visit.getStartLatitude(),
                visit.getStartLongitude(), visit.getStartAccuracyMeters(), visit.getStartedAtDevice(),
                visit.getStartedAtServer(), visit.getDriftSeconds());
    }
}
