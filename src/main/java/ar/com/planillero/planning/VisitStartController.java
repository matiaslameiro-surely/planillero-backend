package ar.com.planillero.planning;

import java.util.UUID;

import ar.com.planillero.planning.dto.StartVisitRequest;
import ar.com.planillero.planning.dto.StartVisitResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inicio de visita, desde la app del operador.
 *
 * <p>Que la visita sea del operador que la inicia no se decide acá: lo valida
 * {@link VisitStartService}.
 */
@RestController
@RequestMapping("/api/v1")
public class VisitStartController {

    private final VisitStartService visitStartService;

    public VisitStartController(VisitStartService visitStartService) {
        this.visitStartService = visitStartService;
    }

    /** Inicia una visita con la ubicación y la hora capturadas por el dispositivo. */
    @PostMapping("/visits/{id}/start")
    @PreAuthorize("hasRole('OPERATOR')")
    public StartVisitResponse start(
            @PathVariable UUID id,
            @Valid @RequestBody StartVisitRequest request,
            Authentication authentication) {
        return visitStartService.start(id, request, authentication.getName());
    }
}
