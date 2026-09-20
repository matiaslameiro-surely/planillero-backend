package ar.com.planillero.planning;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import ar.com.planillero.audit.AuditLog;
import ar.com.planillero.common.ApiException;
import ar.com.planillero.planning.dto.OperatorDto;
import ar.com.planillero.planning.dto.RouteSheetDto;
import ar.com.planillero.planning.dto.RouteSheetItemDto;
import ar.com.planillero.planning.dto.VisitDto;
import ar.com.planillero.user.RoleName;
import ar.com.planillero.user.User;
import ar.com.planillero.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lógica de negocio de la planificación de rutas.
 *
 * <p>Toda consulta y asignación se recorta a la jurisdicción del supervisor autenticado: es la
 * implementación del control de acceso horizontal (OWASP A01). El {@code @PreAuthorize} asegura el
 * rol; este service asegura la zona.
 */
@Service
public class PlanningService {

    /** Las visitas más urgentes van primero dentro de la hoja de ruta. */
    private static final Comparator<Visit> MAS_URGENTE_PRIMERO = Comparator
            .comparingInt((Visit v) -> v.getUrgency().order())
            .reversed()
            .thenComparing(Visit::getCode);

    private final UserRepository userRepository;
    private final VisitRepository visitRepository;
    private final RouteSheetRepository routeSheetRepository;

    public PlanningService(UserRepository userRepository, VisitRepository visitRepository,
            RouteSheetRepository routeSheetRepository) {
        this.userRepository = userRepository;
        this.visitRepository = visitRepository;
        this.routeSheetRepository = routeSheetRepository;
    }

    /** Operadores de la jurisdicción del supervisor, para los selectores de la grilla. */
    @Transactional(readOnly = true)
    public List<OperatorDto> listOperators(String username) {
        User caller = currentUser(username);
        return userRepository.findByRoles_Name(RoleName.OPERATOR).stream()
                .filter(o -> o.getJurisdiction().equals(caller.getJurisdiction()))
                .map(o -> new OperatorDto(o.getId(), o.getUsername(), o.getJurisdiction()))
                .sorted(Comparator.comparing(OperatorDto::username))
                .toList();
    }

    /** Hoja de ruta de un operador para una fecha, ordenada por urgencia. */
    @Transactional(readOnly = true)
    public RouteSheetDto routeSheet(UUID operatorId, LocalDate date, String username) {
        User caller = currentUser(username);
        User operator = requireOperator(operatorId);
        requireSameJurisdiction(caller, operator);
        List<RouteSheet> sheets = routeSheetRepository
                .findByOperatorIdAndRouteDateOrderByPositionAsc(operatorId, date);
        return toRouteSheetDto(operator, date, sheets);
    }

    /**
     * Hoja de ruta del propio operador para una fecha, ordenada por posición.
     *
     * <p>Es la agenda que baja la app móvil. Reusa {@link #routeSheet}: el operador siempre pide su
     * propio identificador, así que nunca puede leer la hoja de otro.
     */
    @Transactional(readOnly = true)
    public RouteSheetDto mySheet(LocalDate date, String username) {
        return routeSheet(currentUser(username).getId(), date, username);
    }

    /** Visitas de la jurisdicción del supervisor, con filtros opcionales. */
    @Transactional(readOnly = true)
    public List<VisitDto> listVisits(VisitStatus status, VisitUrgency urgency, LocalDate date,
            UUID operatorId, String username) {
        User caller = currentUser(username);
        List<Visit> visits = visitRepository.findByJurisdictionOrderByCodeAsc(caller.getJurisdiction());

        if (status != null) {
            visits = visits.stream().filter(v -> v.getStatus() == status).toList();
        }
        if (urgency != null) {
            visits = visits.stream().filter(v -> v.getUrgency() == urgency).toList();
        }
        if (date != null || operatorId != null) {
            visits = restrictToRouteSheets(visits, date, operatorId);
        }

        return visits.stream().map(this::toVisitDto).toList();
    }

    /**
     * Asigna un conjunto de visitas a un operador para una fecha.
     *
     * <p>Si una visita ya estaba asignada ese día a otro operador, se reasigna: se suelta de la hoja
     * anterior y se suma a la nueva (heurística 7). Si ya está asignada al mismo operador es un
     * no-op y responde 400.
     */
    @Transactional
    @AuditLog(eventType = "VISIT_ASSIGNED", entityType = "ROUTE_ASSIGNMENT")
    public RouteSheetDto assign(UUID operatorId, LocalDate date, Set<UUID> visitIds, String username) {
        User caller = currentUser(username);
        User operator = requireOperator(operatorId);
        requireSameJurisdiction(caller, operator);

        List<Visit> visits = visitRepository.findAllById(visitIds);
        if (visits.size() != visitIds.size()) {
            throw ApiException.notFound("visit_not_found", "Una o más visitas no existen.");
        }
        for (Visit visit : visits) {
            requireSameJurisdiction(caller.getJurisdiction(), visit.getJurisdiction());
            // Una visita ya iniciada (IN_PROGRESS) tampoco se reasigna: volvería a ASSIGNED y se podría
            // iniciar de nuevo, pisando la evidencia del primer inicio.
            if (visit.getStatus() == VisitStatus.IN_PROGRESS
                    || visit.getStatus() == VisitStatus.COMPLETED
                    || visit.getStatus() == VisitStatus.CANCELLED) {
                throw ApiException.badRequest("visit_not_assignable",
                        "La visita " + visit.getCode() + " no se puede asignar (estado "
                                + visit.getStatus().name().toLowerCase() + ").");
            }
            // El estado ASSIGNED refleja que la visita tiene al menos una hoja de ruta. Queda
            // persistido para que el filtro por estado del contrato sea consistente con la grilla.
            visit.setStatus(VisitStatus.ASSIGNED);
        }

        // Reasignación: pelar las hojas previas del día para estas visitas. El flush evita chocar
        // con la unicidad (visit_id, route_date) al volver a insertarlas.
        List<RouteSheet> existing = routeSheetRepository.findByVisitIdInAndRouteDate(
                new ArrayList<>(visitIds), date);
        for (RouteSheet sheet : existing) {
            if (sheet.getOperatorId().equals(operatorId)) {
                throw ApiException.badRequest("visit_already_assigned",
                        "Una o más visitas ya están asignadas a este operador para esa fecha.");
            }
        }
        if (!existing.isEmpty()) {
            routeSheetRepository.deleteAll(existing);
            routeSheetRepository.flush();
        }

        // Posición: continúa después de lo que ya tenía la hoja del operador ese día.
        List<RouteSheet> targetSheet = routeSheetRepository
                .findByOperatorIdAndRouteDateOrderByPositionAsc(operatorId, date);
        int position = targetSheet.stream()
                .mapToInt(RouteSheet::getPosition)
                .max()
                .orElse(0);

        List<Visit> ordered = new ArrayList<>(visits);
        ordered.sort(MAS_URGENTE_PRIMERO);
        for (Visit visit : ordered) {
            routeSheetRepository.save(new RouteSheet(operatorId, date, visit, ++position, caller.getId()));
        }

        List<RouteSheet> result = routeSheetRepository
                .findByOperatorIdAndRouteDateOrderByPositionAsc(operatorId, date);
        return toRouteSheetDto(operator, date, result);
    }

    private List<Visit> restrictToRouteSheets(List<Visit> visits, LocalDate date, UUID operatorId) {
        // La combinación de filtros respeta cada dimensión: con operador y fecha, sólo las hojas de
        // ese operador ese día (no las de cualquier operador de la zona con actividad ese día).
        List<RouteSheet> sheets;
        if (date != null && operatorId != null) {
            sheets = routeSheetRepository.findByOperatorIdAndRouteDate(operatorId, date);
        } else if (date != null) {
            sheets = routeSheetRepository.findByRouteDate(date);
        } else {
            sheets = routeSheetRepository.findByOperatorId(operatorId);
        }
        Set<UUID> sheetVisitIds = sheets.stream()
                .map(s -> s.getVisit().getId())
                .collect(Collectors.toSet());
        return visits.stream().filter(v -> sheetVisitIds.contains(v.getId())).toList();
    }

    private User currentUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> ApiException.unauthorized("unauthorized", "Sesión inválida."));
    }

    private User requireOperator(UUID operatorId) {
        User operator = userRepository.findById(operatorId)
                .orElseThrow(() -> ApiException.notFound("operator_not_found", "El operador no existe."));
        boolean isOperator = operator.getRoles().stream().anyMatch(r -> r.getName() == RoleName.OPERATOR);
        if (!isOperator) {
            throw ApiException.notFound("operator_not_found", "El operador no existe.");
        }
        return operator;
    }

    private void requireSameJurisdiction(User caller, User other) {
        requireSameJurisdiction(caller.getJurisdiction(), other.getJurisdiction());
    }

    private void requireSameJurisdiction(String caller, String other) {
        if (!caller.equals(other)) {
            throw ApiException.forbidden("outside_jurisdiction",
                    "No tenés jurisdicción sobre las visitas u operadores de esa zona.");
        }
    }

    private VisitDto toVisitDto(Visit visit) {
        return new VisitDto(visit.getId(), visit.getCode(), visit.getAddress(), visit.getLatitude(),
                visit.getLongitude(), visit.getStatus(), visit.getUrgency());
    }

    private RouteSheetDto toRouteSheetDto(User operator, LocalDate date, List<RouteSheet> sheets) {
        List<RouteSheetItemDto> items = sheets.stream()
                .map(s -> new RouteSheetItemDto(s.getPosition(), toVisitDto(s.getVisit())))
                .toList();
        return new RouteSheetDto(operator.getId(), operator.getUsername(), date, items);
    }
}