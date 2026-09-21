package ar.com.planillero.visits;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ar.com.planillero.audit.AuditLog;
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
 *
 * <p>Hay dos puertas de entrada —la carga en línea y la sincronización diferida— y las dos aplican
 * exactamente las mismas reglas. Lo único que cambia es la transacción y lo que se deja asentado
 * sobre el origen del dato.
 */
@Service
public class VisitFormService {

    private final VisitFormRecordRepository visitRepository;
    private final FormTemplateService templateService;
    private final FormSchemaValidator validator;
    private final ObjectMapper objectMapper;

    public VisitFormService(
            VisitFormRecordRepository visitRepository,
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
    @AuditLog(eventType = "FORM_SUBMITTED", entityType = "VISIT")
    public FormSubmissionResponse submit(UUID visitId, FormSubmissionRequest request) {
        return apply(visitId, request, null).form();
    }

    /**
     * Cómo terminó una carga diferida.
     *
     * @param form           la confirmación del formulario, sea de ahora o de la aplicación original
     * @param alreadyApplied la operación ya estaba aplicada y no se escribió nada
     */
    public record DeferredSubmission(FormSubmissionResponse form, boolean alreadyApplied) {
    }

    /**
     * Carga un formulario que el operador completó sin conexión.
     *
     * <p>Corre en una transacción <strong>propia</strong> ({@code REQUIRES_NEW}) porque es una
     * operación de un lote: si un formulario viene con un dato inválido, su rollback tiene que
     * llevarse sólo esa operación y no las otras 19 del mismo envío. Sin esto, un error de dato en
     * una visita borraría la jornada entera de un operador.
     *
     * <p>Lee la visita con la fila bloqueada y comprueba <strong>dentro de esa misma
     * transacción</strong> si la operación ya se aplicó. Comprobar antes, por fuera, no sirve de
     * nada: entre la consulta y la escritura entran los demás hilos, y como el formulario se guarda
     * con un {@code update} sobre una fila que ya existe, todos escribirían sin violar ningún índice.
     *
     * @param syncOperationId identificador de la operación en el dispositivo; queda guardado y es
     *                        único, así que un reintento no puede duplicar el formulario
     * @throws org.springframework.dao.DataIntegrityViolationException si la misma operación ya se
     *                                       aplicó sobre otra visita
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DeferredSubmission submitDeferred(UUID visitId, FormSubmissionRequest request,
            UUID syncOperationId) {
        return apply(visitId, request, syncOperationId);
    }

    private DeferredSubmission apply(UUID visitId, FormSubmissionRequest request,
            UUID syncOperationId) {
        boolean deferred = syncOperationId != null;
        VisitFormRecord visit = (deferred
                ? visitRepository.findByIdForUpdate(visitId)
                : visitRepository.findById(visitId))
                .orElseThrow(() -> ApiException.notFound(
                        "visit_not_found", "No existe la visita indicada."));

        if (deferred && syncOperationId.equals(visit.getSyncOperationId())) {
            // Ya se aplicó, y con esta misma operación: el reintento no vuelve a escribir. Se
            // devuelve la confirmación original, no una nueva, para que el cliente vea siempre lo
            // mismo que la primera vez.
            return new DeferredSubmission(describeStored(visit), true);
        }

        FormTemplate template = templateService.resolveForSubmission(
                request.templateKey(), request.templateVersion());

        validator.validateOrThrow(template.getSchemaJson(), request.responses());

        Instant submittedAt = Instant.now();
        String responses = objectMapper.writeValueAsString(request.responses());
        if (deferred) {
            visit.submitDeferredForm(template.getId(), responses, submittedAt, syncOperationId);
        } else {
            visit.submitForm(template.getId(), responses, submittedAt);
        }
        // `saveAndFlush` y no `save`: en el camino diferido, la violación del índice único de
        // `sync_operation_id` —la misma operación aplicada sobre otra visita— tiene que salir acá y
        // no al cerrar la transacción, que es donde ya no se puede convertir en un resultado por
        // operación.
        visitRepository.saveAndFlush(visit);

        return new DeferredSubmission(new FormSubmissionResponse(
                visit.getId(), template.getTemplateKey(), template.getVersion(), submittedAt), false);
    }

    /** Reconstruye la confirmación de un formulario ya guardado, tal como se devolvió la primera vez. */
    private FormSubmissionResponse describeStored(VisitFormRecord visit) {
        FormTemplate template = templateService.requireById(visit.getFormTemplateId());
        return new FormSubmissionResponse(visit.getId(), template.getTemplateKey(),
                template.getVersion(), visit.getFormSubmittedAt());
    }
}
