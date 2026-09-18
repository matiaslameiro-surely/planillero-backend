package ar.com.planillero.visits;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.com.planillero.common.ApiException;
import ar.com.planillero.forms.FormSchemaValidator;
import ar.com.planillero.forms.FormTemplate;
import ar.com.planillero.forms.FormTemplateService;
import ar.com.planillero.forms.dto.FormSubmissionRequest;
import ar.com.planillero.forms.dto.FormSubmissionResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Carga del formulario de una visita.
 *
 * <p>El orden importa y es el que hace que nunca quede guardado un formulario inválido: primero se
 * resuelve la visita, después la versión exacta de plantilla, después se valida, y recién con la
 * validación en verde se escribe. Si la validación falla, la excepción corta la transacción antes
 * de cualquier {@code insert} o {@code update}.
 */
@Service
public class VisitFormService {

    private final VisitRepository visitRepository;
    private final FormTemplateService templateService;
    private final FormSchemaValidator validator;
    private final ObjectMapper objectMapper;

    public VisitFormService(
            VisitRepository visitRepository,
            FormTemplateService templateService,
            FormSchemaValidator validator,
            ObjectMapper objectMapper) {
        this.visitRepository = visitRepository;
        this.templateService = templateService;
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    /**
     * Valida el formulario contra su plantilla y, si cumple, lo guarda.
     *
     * @throws ApiException                  {@code 404} si la visita no existe,
     *                                       {@code 400 template_not_found} si la plantilla no existe
     * @throws ar.com.planillero.forms.FormValidationException si el payload no cumple el schema
     */
    @Transactional
    public FormSubmissionResponse submit(UUID visitId, FormSubmissionRequest request) {
        Visit visit = visitRepository.findById(visitId)
                .orElseThrow(() -> ApiException.notFound(
                        "visit_not_found", "No existe la visita indicada."));

        FormTemplate template = templateService.resolveForSubmission(
                request.templateKey(), request.templateVersion());

        validator.validateOrThrow(template.getSchemaJson(), request.responses());

        Instant submittedAt = Instant.now();
        visit.submitForm(template.getId(), objectMapper.writeValueAsString(request.responses()), submittedAt);
        visitRepository.save(visit);

        return new FormSubmissionResponse(
                visit.getId(), template.getTemplateKey(), template.getVersion(), submittedAt);
    }
}
