package ar.com.planillero.sync;

/**
 * Qué hace una operación del lote.
 *
 * <p>Hoy hay un solo valor, y está declarado igual: el tipo viaja en cada operación desde el
 * principio, así que sumar una clase de operación más adelante no cambia la forma del pedido ni
 * obliga a versionar el endpoint.
 */
public enum SyncOperationType {

    /** Carga del formulario tipificado de una visita. */
    VISIT_FORM
}
