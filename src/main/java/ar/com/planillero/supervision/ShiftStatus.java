package ar.com.planillero.supervision;

/**
 * Estados operativos del turno de un operador.
 */
public enum ShiftStatus {
    /** Operador en recorrido o en campo, activo y transmitiendo latidos dentro del SLA. */
    EN_CAMPO,

    /** Operador con demoras en visita o desvío de SLA detectado. */
    DEMORADO,

    /** Operador sin latido reciente en los últimos 10 minutos durante un turno activo. */
    OFFLINE,

    /** Operador que ha concluido todas las visitas de su hoja de ruta y cerrado el turno. */
    TURNO_COMPLETO
}
