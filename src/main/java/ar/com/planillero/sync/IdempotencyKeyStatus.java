package ar.com.planillero.sync;

/** Estado de una clave de idempotencia. */
public enum IdempotencyKeyStatus {

    /** Reservada: hay un lote en proceso con esta clave. */
    IN_PROGRESS,

    /** Cerrada: el lote terminó y su respuesta quedó guardada. */
    COMPLETED
}
