package ar.com.planillero.planning;

/** Urgencia de una visita; define el orden dentro de la hoja de ruta. */
public enum VisitUrgency {

    LOW,
    MEDIUM,
    HIGH;

    /** Peso para ordenar de mayor a menor urgencia. */
    public int order() {
        return switch (this) {
            case LOW -> 1;
            case MEDIUM -> 2;
            case HIGH -> 3;
        };
    }
}