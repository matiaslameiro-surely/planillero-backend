package ar.com.planillero.sync.dto;

import java.util.UUID;

import ar.com.planillero.forms.dto.FormSubmissionRequest;
import ar.com.planillero.sync.SyncOperationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Una operación del lote: algo que el operador hizo sin conexión y ahora se manda al servidor.
 *
 * <p>El {@code clientOperationId} lo genera el dispositivo al encolar la operación y no cambia
 * nunca, ni entre reintentos ni si la operación viaja en otro lote. Es lo que permite reconocerla
 * como ya aplicada aunque la clave del lote sea distinta.
 *
 * @param clientOperationId identificador de la operación, generado por el cliente (UUID v4)
 * @param type              qué clase de operación es
 * @param visitId           visita sobre la que se opera
 * @param form              el formulario a cargar, con la misma forma que la carga en línea
 */
public record SyncOperationRequest(
        @NotNull(message = "es obligatorio") UUID clientOperationId,
        @NotNull(message = "es obligatorio") SyncOperationType type,
        @NotNull(message = "es obligatorio") UUID visitId,
        @NotNull(message = "es obligatorio") @Valid FormSubmissionRequest form) {
}
