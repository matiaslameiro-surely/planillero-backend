package ar.com.planillero.planning;

/** Estado de una visita a lo largo de su ciclo de vida. */
public enum VisitStatus {

    /** Todavía no tiene plan de ejecución. */
    PENDING("pendiente"),

    /** Tiene al menos una hoja de ruta asignada. */
    ASSIGNED("asignada"),
    /** El operador la inició en el domicilio y quedó registrada su presencia. */
    IN_PROGRESS("en curso"),

    /** Ya fue atendida por un operador. */
    COMPLETED("completada"),

    /** Se descartó. */
    CANCELLED("cancelada");

    private final String label;

    VisitStatus(String label) {
        this.label = label;
    }

    /**
     * Nombre del estado en español y en minúscula, para usarlo en medio de un mensaje dirigido a una
     * persona: «La visita V-1001 no se puede asignar (estado en curso).». Son las mismas palabras que
     * muestran el backoffice y la app.
     *
     * <p>La API sigue exponiendo el estado por {@link #name()}: esto es sólo para texto.
     */
    public String label() {
        return label;
    }
}
