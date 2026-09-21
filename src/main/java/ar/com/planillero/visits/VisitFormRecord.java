package ar.com.planillero.visits;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * El formulario cargado en una visita.
 *
 * <p>Mapea <strong>sólo</strong> las columnas del formulario de {@code visits.visits}. La visita en sí
 * —dirección, estado, urgencia, inicio— es de {@link ar.com.planillero.planning.Visit}, y esta
 * entidad no la duplica ni la toca: nunca inserta filas y sólo escribe sus tres columnas. Así el
 * formulario no queda acoplado al ciclo de vida de la visita, que evoluciona en otras tareas.
 *
 * <p>Las respuestas se guardan como el texto JSON que mandó el cliente y no como un grafo de
 * objetos: así se conserva exactamente lo enviado, que es lo que después firma y audita el resto del
 * sistema. Al ser un parámetro más de la sentencia, nada de lo que venga adentro puede alterar el
 * SQL (OWASP A03).
 */
@Entity
@Table(name = "visits", schema = "visits")
public class VisitFormRecord {

    @Id
    private UUID id;

    /** Versión exacta de plantilla contra la que se validaron las respuestas. */
    @Column(name = "form_template_id")
    private UUID formTemplateId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "responses_json")
    private String responsesJson;

    @Column(name = "form_submitted_at")
    private Instant formSubmittedAt;

    /**
     * Operación de sincronización que trajo este formulario, o {@code null} si se cargó en línea.
     *
     * <p>La columna tiene un índice único: es lo que impide que un reintento del dispositivo
     * escriba el formulario dos veces.
     */
    @Column(name = "sync_operation_id")
    private UUID syncOperationId;

    /** El formulario llegó por el endpoint de sincronización por lote y no por la carga en línea. */
    @Column(name = "synced_deferred", nullable = false)
    private boolean syncedDeferred;

    @Column(name = "synced_at")
    private Instant syncedAt;

    protected VisitFormRecord() {
        // Requerido por JPA.
    }

    public UUID getId() {
        return id;
    }

    public UUID getFormTemplateId() {
        return formTemplateId;
    }

    public String getResponsesJson() {
        return responsesJson;
    }

    public Instant getFormSubmittedAt() {
        return formSubmittedAt;
    }

    /**
     * Registra el formulario ya validado.
     *
     * <p>Se guardan juntos el JSON y la plantilla que lo aceptó: uno sin el otro no se puede
     * interpretar. No cambia el estado de la visita: eso es del ciclo de vida de planificación.
     */
    public void submitForm(UUID formTemplateId, String responsesJson, Instant submittedAt) {
        this.formTemplateId = formTemplateId;
        this.responsesJson = responsesJson;
        this.formSubmittedAt = submittedAt;
    }

    /**
     * Registra el formulario que llegó por sincronización diferida.
     *
     * <p>Además del formulario deja asentado <em>de dónde vino</em>: qué operación del dispositivo lo
     * trajo y cuándo se recibió. El identificador de operación no es sólo trazabilidad — es la
     * columna con índice único que hace que el reintento del dispositivo no pueda duplicar el acta.
     */
    public void submitDeferredForm(UUID formTemplateId, String responsesJson, Instant submittedAt,
            UUID syncOperationId) {
        submitForm(formTemplateId, responsesJson, submittedAt);
        this.syncOperationId = syncOperationId;
        this.syncedDeferred = true;
        this.syncedAt = submittedAt;
    }

    public UUID getSyncOperationId() {
        return syncOperationId;
    }

    public boolean isSyncedDeferred() {
        return syncedDeferred;
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }
}
