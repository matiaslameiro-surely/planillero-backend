package ar.com.planillero.planning;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import ar.com.planillero.planning.dto.AssignRequest;
import ar.com.planillero.planning.dto.OperatorDto;
import ar.com.planillero.planning.dto.RouteSheetDto;
import ar.com.planillero.planning.dto.VisitDto;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de planificación de rutas.
 *
 * <p>El acceso horizontal (qué zona puede operar cada supervisor) no se decide acá: lo valida
 * {@link PlanningService} sobre la jurisdicción del usuario autenticado.
 */
@RestController
@RequestMapping("/api/v1")
public class PlanningController {

    private final PlanningService planningService;

    public PlanningController(PlanningService planningService) {
        this.planningService = planningService;
    }

    /** Operadores de la jurisdicción del supervisor, para los selectores de la grilla. */
    @GetMapping("/operators")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public List<OperatorDto> operators(Authentication authentication) {
        return planningService.listOperators(authentication.getName());
    }

    /** Hoja de ruta de un operador para una fecha, ordenada por urgencia. */
    @GetMapping("/operators/{operatorId}/route-sheets")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public RouteSheetDto routeSheet(
            @PathVariable UUID operatorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication authentication) {
        return planningService.routeSheet(operatorId, date, authentication.getName());
    }

    /** Visitas de la jurisdicción del supervisor, con filtros opcionales. */
    @GetMapping("/visits")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public List<VisitDto> visits(
            @RequestParam(required = false) VisitStatus status,
            @RequestParam(required = false) VisitUrgency urgency,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) UUID operatorId,
            Authentication authentication) {
        return planningService.listVisits(status, urgency, date, operatorId, authentication.getName());
    }

    /** Asigna visitas a un operador para una fecha; reasigna si ya estaban con otro operador. */
    @PostMapping("/visits/assign")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public RouteSheetDto assign(@Valid @RequestBody AssignRequest request, Authentication authentication) {
        return planningService.assign(request.operatorId(), request.date(), request.visitIds(),
                authentication.getName());
    }
}