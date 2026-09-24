package ar.com.planillero.sync;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import ar.com.planillero.common.ApiException;
import ar.com.planillero.forms.FormTemplate;
import ar.com.planillero.forms.FormTemplateRepository;
import ar.com.planillero.forms.FormValidationException;
import ar.com.planillero.forms.dto.FormSubmissionResponse;
import ar.com.planillero.planning.VisitAccessGuard;
import ar.com.planillero.sync.dto.SyncBatchRequest;
import ar.com.planillero.sync.dto.SyncBatchResponse;
import ar.com.planillero.sync.dto.SyncOperationRequest;
import ar.com.planillero.sync.dto.SyncOperationResult;
import ar.com.planillero.user.User;
import ar.com.planillero.user.UserRepository;
import ar.com.planillero.visits.VisitFormRecord;
import ar.com.planillero.visits.VisitFormRecordRepository;
import ar.com.planillero.visits.VisitFormService;
import tools.jackson.databind.ObjectMapper;

/**
 * Aplica un lote de operaciones que el operador hizo sin conexión.
 *
 * <p>No tiene reglas de negocio propias: valida y guarda exactamente lo mismo que la carga en línea,
 * delegando en {@link VisitFormService}. Lo que agrega es el transporte y las dos garantías de no
 * duplicación —por lote y por operación—, que es todo lo que separa un reintento de un acta
 * duplicada en un expediente.
 *
 * <p><strong>No es transaccional a propósito.</strong> La reserva de la clave, cada operación y el
 * cierre de la clave se confirman por separado: una transacción que abarcara todo desharía la
 * reserva al fallar, y entonces dos envíos simultáneos podrían procesarse los dos.
 */
@Service
public class SyncService {

    private final IdempotencyService idempotency;
    private final VisitFormService visitFormService;
    private final VisitFormRecordRepository visitFormRepository;
    private final FormTemplateRepository templateRepository;
    private final UserRepository userRepository;
    private final VisitAccessGuard visitAccessGuard;
    private final ObjectMapper objectMapper;

    public SyncService(IdempotencyService idempotency, VisitFormService visitFormService,
            VisitFormRecordRepository visitFormRepository, FormTemplateRepository templateRepository,
            UserRepository userRepository, VisitAccessGuard visitAccessGuard, ObjectMapper objectMapper) {
        this.idempotency = idempotency;
        this.visitFormService = visitFormService;
        this.visitFormRepository = visitFormRepository;
        this.templateRepository = templateRepository;
        this.userRepository = userRepository;
        this.visitAccessGuard = visitAccessGuard;
        this.objectMapper = objectMapper;
    }

    /**
     * Procesa el lote, o devuelve la respuesta del envío original si esta clave ya se usó.
     *
     * @throws ApiException {@code 409} si la clave está en uso o se reusó para otro cuerpo
     */
    public SyncBatchResponse process(UUID idempotencyKey, SyncBatchRequest request, String username) {
        User caller = userRepository.findByUsername(username)
                .orElseThrow(() -> ApiException.unauthorized("unauthorized", "Sesión inválida."));

        String requestHash = IdempotencyService.hash(objectMapper.writeValueAsString(request));

        if (idempotency.reserve(idempotencyKey, caller.getId(), requestHash)
                instanceof IdempotencyService.Reservation.AlreadyCompleted cached) {
            // Se devuelve lo guardado, no se vuelve a procesar. Aunque los datos hayan cambiado
            // desde entonces, el cliente tiene que recibir la misma respuesta que la primera vez.
            return objectMapper.readValue(cached.responseJson(), SyncBatchResponse.class);
        }

        try {
            SyncBatchResponse response =
                    new SyncBatchResponse(request.operations().stream()
                            .map(operation -> apply(operation, username))
                            .toList());
            idempotency.complete(idempotencyKey, objectMapper.writeValueAsString(response));
            return response;
        } catch (RuntimeException unexpected) {
            // La clave queda libre: si no, el cliente recibiría 409 para siempre y no podría
            // reintentar nunca. El cierre entra en el intento porque también puede fallar, y una
            // clave reservada sin respuesta guardada es exactamente el estado que traba la cola.
            //
            // Esto cubre el fallo que se puede atrapar. Si el proceso muere, no hay catch que valga:
            // de eso se encarga el vencimiento de la reserva en IdempotencyService.
            idempotency.release(idempotencyKey);
            throw unexpected;
        }
    }

    /**
     * Aplica una operación y traduce su desenlace a un resultado.
     *
     * <p>Nunca propaga: el error de una operación es un dato del lote, no un fallo del pedido. Las
     * excepciones de negocio ya traen su código estable y se copian tal cual, así que el cliente
     * distingue «esta visita no existe» de «este formulario no cumple el schema» sin leer el texto.
     */
    private SyncOperationResult apply(SyncOperationRequest operation, String username) {
        UUID operationId = operation.clientOperationId();

        // El identificador de operación se exige tan aleatorio como la clave de lote, y por el mismo
        // motivo: si dos dispositivos pudieran generar el mismo, uno vería la operación del otro
        // como ya aplicada y su acta nunca se guardaría. Se rechaza sólo esta operación, no el lote.
        if (!SyncController.esUuidV4(operationId)) {
            return SyncOperationResult.failed(operationId, "operation_id_invalid",
                    "El clientOperationId tiene que ser un UUID versión 4.");
        }

        // El acceso a la visita va antes de la detección de repetidas: una operación sobre una visita
        // ajena se rechaza aunque su identificador ya se haya aplicado. Una operación sobre una visita
        // propia que reusa el identificador de otra visita sigue volviendo DUPLICATE, como define PLAN-14.
        try {
            visitAccessGuard.requireAccess(operation.visitId(), username);
        } catch (ApiException rejected) {
            return SyncOperationResult.failed(operationId, rejected.getCode(), rejected.getMessage());
        }

        // Camino rápido del reintento: la operación ya se aplicó en un envío anterior.
        SyncOperationResult alreadyApplied = resolveIfAlreadyApplied(operationId);
        if (alreadyApplied != null) {
            return alreadyApplied;
        }

        try {
            VisitFormService.DeferredSubmission submission = visitFormService.submitDeferred(
                    operation.visitId(), operation.form(), operationId);
            // El servicio comprueba «ya aplicada» con la fila bloqueada, que es donde la respuesta
            // es confiable: dos hilos simultáneos no pueden verla los dos como pendiente.
            return submission.alreadyApplied()
                    ? SyncOperationResult.duplicate(operationId, submission.form())
                    : SyncOperationResult.applied(operationId, submission.form());
        } catch (DataIntegrityViolationException raced) {
            // La operación ya está aplicada sobre otra visita: el índice único de
            // `sync_operation_id` la frenó, que es exactamente para lo que está.
            SyncOperationResult duplicate = resolveIfAlreadyApplied(operationId);
            return duplicate != null
                    ? duplicate
                    : SyncOperationResult.failed(operationId, "operation_conflict",
                            "La operación no se pudo aplicar por un conflicto de escritura. Reintentá.");
        } catch (FormValidationException invalid) {
            return SyncOperationResult.failed(operationId, "form_validation_failed",
                    invalid.getMessage());
        } catch (ApiException rejected) {
            return SyncOperationResult.failed(operationId, rejected.getCode(), rejected.getMessage());
        }
    }

    /** El resultado de la aplicación original, o {@code null} si la operación nunca se aplicó. */
    private SyncOperationResult resolveIfAlreadyApplied(UUID operationId) {
        return visitFormRepository.findBySyncOperationId(operationId)
                .map(record -> SyncOperationResult.duplicate(operationId, describe(record)))
                .orElse(null);
    }

    /** Reconstruye la confirmación original de un formulario ya guardado. */
    private FormSubmissionResponse describe(VisitFormRecord record) {
        FormTemplate template = templateRepository.findById(record.getFormTemplateId())
                .orElseThrow(() -> new IllegalStateException(
                        "El formulario de la visita " + record.getId() + " apunta a una plantilla que no existe."));
        return new FormSubmissionResponse(record.getId(), template.getTemplateKey(),
                template.getVersion(), record.getFormSubmittedAt());
    }
}
