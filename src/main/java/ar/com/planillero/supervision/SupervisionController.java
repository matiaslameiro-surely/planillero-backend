package ar.com.planillero.supervision;

import java.time.LocalDate;
import java.util.List;

import ar.com.planillero.supervision.dto.DashboardSummaryDto;
import ar.com.planillero.supervision.dto.HeartbeatRequest;
import ar.com.planillero.supervision.dto.HeartbeatResponse;
import ar.com.planillero.supervision.dto.OperatorStatusDto;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST del Tablero Central de Supervisión y telemetría de operadores.
 */
@RestController
@RequestMapping("/api/v1/supervision")
public class SupervisionController {

    private final SupervisionService supervisionService;

    public SupervisionController(SupervisionService supervisionService) {
        this.supervisionService = supervisionService;
    }

    /**
     * Resumen analítico de KPIs y excepciones operativas para supervisión.
     */
    @GetMapping("/tablero-resumen")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMINISTRATOR')")
    public DashboardSummaryDto getDashboardSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication authentication) {
        return supervisionService.getDashboardSummary(date, authentication.getName());
    }

    /**
     * Grilla con estado en vivo y telemetría de cada operador.
     */
    @GetMapping("/operadores/estado")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMINISTRATOR')")
    public List<OperatorStatusDto> getOperatorsStatus(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication authentication) {
        return supervisionService.getOperatorsStatus(date, authentication.getName());
    }

    /**
     * Ingesta de latido periódico (heartbeat) desde el cliente móvil del operador.
     */
    @PostMapping("/heartbeat")
    @PreAuthorize("hasRole('OPERATOR')")
    public HeartbeatResponse recordHeartbeat(
            @Valid @RequestBody HeartbeatRequest request,
            Authentication authentication) {
        return supervisionService.recordHeartbeat(request, authentication.getName());
    }
}
