package ar.com.planillero.sync.dto;

/** Cómo terminó una operación del lote. */
public enum SyncOperationStatus {

    /** Se aplicó ahora: el formulario quedó guardado. */
    APPLIED,

    /** Ya estaba aplicada de antes; no se escribió nada. Para el cliente es un éxito igual. */
    DUPLICATE,

    /** No se pudo aplicar. El motivo viene en el resultado y no se reintenta tal cual. */
    FAILED
}
