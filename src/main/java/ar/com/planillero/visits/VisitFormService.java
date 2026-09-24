package ar.com.planillero.visits;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ar.com.planillero.audit.AuditChainService;
import ar.com.planillero.audit.AuditLog;
import ar.com.planillero.audit.AuditRequestContext;
import ar.com.planillero.common.ApiException;
import ar.com.planillero.forms.FormSchemaValidator;
import ar.com.planillero.forms.FormTemplate;
import ar.com.planillero.forms.FormTemplateRepository;
import ar.com.planillero.forms.FormTemplateService;
import ar.com.planillero.forms.dto.FormSubmissionRequest;
import ar.com.planillero.forms.dto.FormSubmissionResponse;
import ar.com.planillero.planning.Visit;
import ar.com.planillero.planning.VisitAccessGuard;
import ar.com.planillero.planning.VisitRepository;
import ar.com.planillero.visits.dto.VisitFormDetailDto;
import tools.jackson.databind.JsonNode;
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
 *
 * <p>El acceso a la visita ({@link VisitAccessGuard}) también se exige en las dos: la carga en línea
 * lo valida acá y la diferida, en {@link ar.com.planillero.sync.SyncService}, que es su único
 * llamador y lo tiene que hacer antes de detectar operaciones repetidas.
 */
@Service
public class VisitFormService {

    private final VisitFormRecordRepository visitRecordRepository;
    private final VisitRepository planningVisitRepository;
    private final FormTemplateService templateService;
    private final FormTemplateRepository templateRepository;
    private final FormSchemaValidator validator;
    private final ObjectMapper objectMapper;
    private final AuditChainService auditChainService;
    private final AuditRequestContext auditRequestContext;
    private final VisitAccessGuard visitAccessGuard;

    public VisitFormService(
            VisitFormRecordRepository visitRecordRepository,
            VisitRepository planningVisitRepository,
            FormTemplateService templateService,
            FormTemplateRepository templateRepository,
            FormSchemaValidator validator,
            ObjectMapper objectMapper,
            AuditChainService auditChainService,
            AuditRequestContext auditRequestContext,
            VisitAccessGuard visitAccessGuard) {
        this.visitRecordRepository = visitRecordRepository;
        this.planningVisitRepository = planningVisitRepository;
        this.templateService = templateService;
        this.templateRepository = templateRepository;
        this.validator = validator;
        this.objectMapper = objectMapper;
        this.auditChainService = auditChainService;
        this.auditRequestContext = auditRequestContext;
        this.visitAccessGuard = visitAccessGuard;
    }

    /**
     * Valida el formulario contra su plantilla y, si cumple, lo guarda.
     *
     * @throws ApiException                  {@code 404} si la visita no existe,
     *                                       {@code 403} si el usuario no tiene acceso a la visita,
     *                                       {@code 400 template_not_found} si la plantilla no existe
     * @throws ar.com.planillero.forms.FormValidationException si el payload no cumple el schema
     */
    @Transactional
    @AuditLog(eventType = "FORM_SUBMITTED", entityType = "VISIT")
    public FormSubmissionResponse submit(UUID visitId, FormSubmissionRequest request, String username) {
        visitAccessGuard.requireAccess(visitId, username);
        return applyOnline(visitId, request).form();
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
     * <p>No valida el acceso a la visita: lo hace {@link ar.com.planillero.sync.SyncService} antes de
     * llamar.
     *
     * @param syncOperationId identificador de la operación en el dispositivo; queda guardado y es
     *                        único, así que un reintento no puede duplicar el formulario
     * @throws org.springframework.dao.DataIntegrityViolationException si la misma operación ya se
     *                                       aplicó sobre otra visita
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DeferredSubmission submitDeferred(UUID visitId, FormSubmissionRequest request,
            UUID syncOperationId) {
        return applyDeferred(visitId, request, syncOperationId);
    }

    private DeferredSubmission applyOnline(UUID visitId, FormSubmissionRequest request) {
        // Carga en línea: lee el registro de formulario (sin lock pesimista)
        VisitFormRecord record = visitRecordRepository.findById(visitId)
                .orElseThrow(() -> ApiException.notFound(
                        "visit_not_found", "No existe la visita indicada."));

        return applyCommon(record, request, null);
    }

    private DeferredSubmission applyDeferred(UUID visitId, FormSubmissionRequest request,
            UUID syncOperationId) {
        // Carga diferida: lee la visita de planificación con lock pesimista para evitar carreras
        Visit visit = planningVisitRepository.findByIdForUpdate(visitId)
                .orElseThrow(() -> ApiException.notFound(
                        "visit_not_found", "No existe la visita indicada."));

        // Verificar si ya se aplicó esta operación
        Optional<VisitFormRecord> existingRecord = visitRecordRepository.findBySyncOperationId(syncOperationId);
        if (existingRecord.isPresent() && existingRecord.get().getId().equals(visit.getId())) {
            return new DeferredSubmission(describeStored(existingRecord.get()), true);
        }

        // Obtener el registro de formulario asociado a esta visita
        VisitFormRecord record = visitRecordRepository.findById(visit.getId())
                .orElseThrow(() -> ApiException.notFound(
                        "visit_not_found", "No existe el registro de formulario para la visita."));

        return applyCommon(record, request, syncOperationId);
    }

    private DeferredSubmission applyCommon(VisitFormRecord record, FormSubmissionRequest request,
            UUID syncOperationId) {
        boolean deferred = syncOperationId != null;

        if (deferred && syncOperationId.equals(record.getSyncOperationId())) {
            // Ya se aplicó, y con esta misma operación: el reintento no vuelve a escribir.
            return new DeferredSubmission(describeStored(record), true);
        }

        FormTemplate template = templateService.resolveForSubmission(
                request.templateKey(), request.templateVersion());

        validator.validateOrThrow(template.getSchemaJson(), request.responses());

        Instant submittedAt = Instant.now();
        String responses = objectMapper.writeValueAsString(request.responses());
        if (deferred) {
            record.submitDeferredForm(template.getId(), responses, submittedAt, syncOperationId);
        } else {
            record.submitForm(template.getId(), responses, submittedAt);
        }
        // saveAndFlush en el repo del registro de formulario
        visitRecordRepository.saveAndFlush(record);

        FormSubmissionResponse response = new FormSubmissionResponse(
                record.getId(), template.getTemplateKey(), template.getVersion(), submittedAt);

        if (deferred) {
            auditDeferred(record.getId(), request, response, syncOperationId);
        }

        return new DeferredSubmission(response, false);
    }

    private void auditDeferred(UUID visitId, FormSubmissionRequest request,
            FormSubmissionResponse response, UUID syncOperationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("method", "submitDeferred");
        payload.put("args", Map.of(
                "arg0", visitId.toString(),
                "arg1", request,
                "arg2", syncOperationId.toString()
        ));
        payload.put("result", response);

        auditChainService.append(
                "FORM_SUBMITTED",
                "VISIT",
                visitId.toString(),
                auditRequestContext.currentUsername(),
                auditRequestContext.currentIp(),
                auditRequestContext.currentDeviceId(),
                payload);
    }

    /** Reconstruye la confirmación de un formulario ya guardado, tal como se devolvió la primera vez. */
    private FormSubmissionResponse describeStored(VisitFormRecord record) {
        FormTemplate template = templateService.requireById(record.getFormTemplateId());
        return new FormSubmissionResponse(record.getId(), template.getTemplateKey(),
                template.getVersion(), record.getFormSubmittedAt());
    }

    /**
     * Devuelve la visita con su formulario cargado, para el visor del expediente digital.
     *
     * <p>Reino de solo lectura: no valida nada ni muta estado. Una visita sin formulario devuelve los
     * campos de formulario en {@code null}; si la visita no existe, {@code 404 visit_not_found}, y si
     * es de otra jurisdicción, {@code 403 outside_jurisdiction}.
     */
    @Transactional(readOnly = true)
    public VisitFormDetailDto getFormDetail(UUID visitId, String username) {
        Visit visit = visitAccessGuard.requireAccess(visitId, username);
        VisitFormRecord record = visitRecordRepository.findById(visitId)
                .orElseThrow(() -> ApiException.notFound(
                        "visit_not_found", "No existe la visita indicada."));

        UUID formTemplateId = record.getFormTemplateId();
        if (formTemplateId == null) {
            return new VisitFormDetailDto(
                    visit.getId(), visit.getCode(), visit.getAddress(),
                    visit.getLatitude(), visit.getLongitude(), visit.getJurisdiction(),
                    visit.getStatus(), visit.getUrgency(), visit.getCreatedAt(),
                    null, null, null, null, null, null);
        }

        FormTemplate template = templateRepository.findById(formTemplateId).orElse(null);
        JsonNode responses = objectMapper.readTree(record.getResponsesJson());
        return new VisitFormDetailDto(
                visit.getId(), visit.getCode(), visit.getAddress(),
                visit.getLatitude(), visit.getLongitude(), visit.getJurisdiction(),
                visit.getStatus(), visit.getUrgency(), visit.getCreatedAt(),
                formTemplateId,
                template != null ? template.getTemplateKey() : null,
                template != null ? template.getVersion() : null,
                template != null ? template.getName() : null,
                responses,
                record.getFormSubmittedAt());
    }
}