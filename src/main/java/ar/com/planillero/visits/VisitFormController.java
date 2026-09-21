package ar.com.planillero.visits;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.com.planillero.forms.dto.FormSubmissionRequest;
import ar.com.planillero.forms.dto.FormSubmissionResponse;
import ar.com.planillero.visits.dto.VisitFormDetailDto;
import jakarta.validation.Valid;

/**
 * Carga del formulario tipificado de una visita.
 *
 * <p>Es el endpoint que usa el operario desde el móvil cuando termina de completar la planilla.
 */
@RestController
@RequestMapping("/api/v1/visitas")
public class VisitFormController {

    private final VisitFormService service;

    public VisitFormController(VisitFormService service) {
        this.service = service;
    }

    /**
     * Valida y guarda el formulario de una visita.
     *
     * <p>Responde {@code 400} con la lista completa de campos con problema si el payload no cumple
     * el schema de la plantilla.
     */
    @PostMapping("/{id}/formulario")
    @PreAuthorize("hasAnyRole('OPERATOR', 'SUPERVISOR', 'ADMINISTRATOR')")
    public FormSubmissionResponse submitForm(
            @PathVariable("id") UUID id,
            @Valid @RequestBody FormSubmissionRequest request) {
        return service.submit(id, request);
    }

    /**
     * Devuelve la visita con su formulario cargado, para el visor del expediente digital del
     * backoffice.
     *
     * <p>Es un endpoint de administración, no de operación: sólo supervisor y admin lo consumen.
     * Una visita sin formulario devuelve los campos de formulario en {@code null}.
     */
    @GetMapping("/{id}/formulario")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMINISTRATOR')")
    public VisitFormDetailDto getFormDetail(@PathVariable("id") UUID id) {
        return service.getFormDetail(id);
    }
}
