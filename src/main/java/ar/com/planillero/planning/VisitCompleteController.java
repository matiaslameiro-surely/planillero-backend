package ar.com.planillero.planning;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.com.planillero.planning.dto.CompleteVisitResponse;

/**
 * Finalización de visita pericial, desde la app móvil del operador.
 */
@RestController
@RequestMapping("/api/v1")
public class VisitCompleteController {

    private final VisitCompleteService visitCompleteService;

    public VisitCompleteController(VisitCompleteService visitCompleteService) {
        this.visitCompleteService = visitCompleteService;
    }

    /** Finaliza una visita en curso. */
    @PostMapping("/visits/{id}/complete")
    @PreAuthorize("hasRole('OPERATOR')")
    public CompleteVisitResponse complete(
            @PathVariable UUID id,
            Authentication authentication) {
        return visitCompleteService.complete(id, authentication.getName());
    }
}
