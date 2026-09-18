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
 * Visita a la que se le carga un formulario.
 *
 * <p>Deliberadamente mínima: identificador, estado, alta y el formulario. El ciclo de vida completo
 * —asignación, geolocalización, inicio y cierre— es de otras tareas; esta entidad existe para que
 * las respuestas tengan dónde colgarse.
 *
 * <p>Las respuestas se guardan como el texto JSON que mandó el cliente y no como un grafo de
 * objetos: así se conserva exactamente lo enviado, que es lo que después firma y audita el resto del
 * sistema. Al ser un parámetro más de la sentencia, nada de lo que venga adentro puede alterar el
 * SQL (OWASP A03).
 */
@Entity
@Table(name = "visits", schema = "visits")
public class Visit {

    @Id
    private UUID id;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "PENDING";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Versión exacta de plantilla contra la que se validaron las respuestas. */
    @Column(name = "form_template_id")
    private UUID formTemplateId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "responses_json")
    private String responsesJson;

    @Column(name = "form_submitted_at")
    private Instant formSubmittedAt;

    protected Visit() {
        // Requerido por JPA.
    }

    /**
     * Crea una visita pendiente.
     *
     * <p>Hoy la usan los tests y el seed: el alta real de visitas llega con el módulo de
     * asignación.
     */
    public static Visit create() {
        Visit visit = new Visit();
        visit.id = UUID.randomUUID();
        visit.createdAt = Instant.now();
        return visit;
    }

    public UUID getId() {
        return id;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
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
     * interpretar.
     */
    public void submitForm(UUID formTemplateId, String responsesJson, Instant submittedAt) {
        this.formTemplateId = formTemplateId;
        this.responsesJson = responsesJson;
        this.formSubmittedAt = submittedAt;
        this.status = "FORM_SUBMITTED";
    }
}
