package ar.com.planillero.planning;

/** Estado de una visita a lo largo de su ciclo de vida. */
public enum VisitStatus {

    /** Todavía no tiene plan de ejecución. */
    PENDING,

    /** Tiene al menos una hoja de ruta asignada. */
    ASSIGNED,
    /** El operador la inició en el domicilio y quedó registrada su presencia. */
    IN_PROGRESS,

    /** Ya fue atendida por un operador. */
    COMPLETED,

    /** Se descartó. */
    CANCELLED
}