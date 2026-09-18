package ar.com.planillero.planning;

/** Estado de una visita a lo largo de su ciclo de vida. */
public enum VisitStatus {

    /** Todavía no tiene plan de ejecución. */
    PENDING,

    /** Tiene al menos una hoja de ruta asignada. */
    ASSIGNED,

    /** Ya fue atendida por un operador. */
    COMPLETED,

    /** Se descartó. */
    CANCELLED
}