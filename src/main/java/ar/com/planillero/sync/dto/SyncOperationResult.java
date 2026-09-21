package ar.com.planillero.sync.dto;

import java.util.UUID;

import ar.com.planillero.forms.dto.FormSubmissionResponse;

/**
 * Cómo terminó una operación.
 *
 * <p>{@code DUPLICATE} trae el mismo {@code form} que devolvió la primera aplicación, no uno nuevo:
 * para el cliente, reintentar una operación ya aplicada tiene que ser indistinguible de haberla
 * aplicado recién, o la cola se quedaría con operaciones que no sabe cómo cerrar.
 *
 * @param clientOperationId el identificador que mandó el cliente, para que pueda casar el resultado
 * @param status            cómo terminó
 * @param form              confirmación del formulario, en {@code APPLIED} y {@code DUPLICATE}
 * @param error             código estable del fallo, sólo en {@code FAILED}
 * @param message           explicación del fallo, sólo en {@code FAILED}
 */
public record SyncOperationResult(
        UUID clientOperationId,
        SyncOperationStatus status,
        FormSubmissionResponse form,
        String error,
        String message) {

    public static SyncOperationResult applied(UUID clientOperationId, FormSubmissionResponse form) {
        return new SyncOperationResult(clientOperationId, SyncOperationStatus.APPLIED, form, null, null);
    }

    public static SyncOperationResult duplicate(UUID clientOperationId, FormSubmissionResponse form) {
        return new SyncOperationResult(clientOperationId, SyncOperationStatus.DUPLICATE, form, null, null);
    }

    public static SyncOperationResult failed(UUID clientOperationId, String error, String message) {
        return new SyncOperationResult(clientOperationId, SyncOperationStatus.FAILED, null, error, message);
    }
}
