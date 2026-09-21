package ar.com.planillero.sync.dto;

import java.util.List;

/**
 * Resultado del lote, con una entrada por operación y en el mismo orden en que llegaron.
 *
 * <p>El lote responde {@code 200} aunque alguna operación haya fallado: el código HTTP describe si
 * el lote se procesó, y cada operación lleva su propio desenlace. Si una operación con un dato mal
 * hiciera fallar todo el pedido, el operador perdería la jornada entera por un formulario.
 */
public record SyncBatchResponse(List<SyncOperationResult> results) {
}
