package ar.com.planillero.forms;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.com.planillero.forms.dto.FormTemplateResponse;

/**
 * Catálogo de plantillas de formulario.
 *
 * <p>Lo consume el renderizador dinámico del cliente: con el JSON Schema de cada plantilla arma los
 * campos sin tener nada cableado.
 *
 * <p>Accesible para cualquier usuario autenticado: el operario que completa el formulario necesita
 * leer la plantilla tanto como el supervisor que la revisa.
 */
@RestController
@RequestMapping("/api/v1/plantillas")
public class FormTemplateController {

    private final FormTemplateService service;

    public FormTemplateController(FormTemplateService service) {
        this.service = service;
    }

    /** Todas las plantillas vigentes, con su schema completo. */
    @GetMapping
    @PreAuthorize("hasAnyRole('OPERATOR', 'SUPERVISOR', 'ADMINISTRATOR')")
    public List<FormTemplateResponse> list() {
        return service.listActive();
    }

    /** La última versión vigente de una plantilla. */
    @GetMapping("/{clave}")
    @PreAuthorize("hasAnyRole('OPERATOR', 'SUPERVISOR', 'ADMINISTRATOR')")
    public FormTemplateResponse getByKey(@PathVariable("clave") String clave) {
        return service.getLatestActive(clave);
    }
}
