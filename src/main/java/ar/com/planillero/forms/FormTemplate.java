package ar.com.planillero.forms;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Plantilla de formulario tipificado.
 *
 * <p>La forma del formulario no está en el código: viaja adentro de {@code schemaJson} como un JSON
 * Schema 2020-12. Agregar, sacar o restringir un campo es publicar una versión nueva de la
 * plantilla.
 *
 * <p>Una plantilla publicada es <strong>inmutable</strong>: la combinación clave + versión es única
 * y un trigger de la base rechaza cualquier intento de cambiarle el schema. Lo único que se puede
 * modificar es {@code active}, para dejar de ofrecerla sin borrar el historial.
 *
 * <p>El schema se guarda como texto y no como un grafo de objetos para conservar exactamente lo que
 * se publicó, incluido el orden de las claves.
 */
@Entity
@Table(name = "form_templates", schema = "forms")
public class FormTemplate {

    @Id
    private UUID id;

    /** Identificador estable de la plantilla, independiente de la versión. */
    @Column(name = "template_key", nullable = false, length = 80)
    private String templateKey;

    /** Número de versión, creciente por clave. Empieza en 1. */
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    /** El JSON Schema de la plantilla, tal cual se publicó. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "schema_json", nullable = false)
    private String schemaJson;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected FormTemplate() {
        // Requerido por JPA.
    }

    public FormTemplate(String templateKey, int version, String name, String description, String schemaJson) {
        this.id = UUID.randomUUID();
        this.templateKey = templateKey;
        this.version = version;
        this.name = name;
        this.description = description;
        this.schemaJson = schemaJson;
        this.active = true;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getTemplateKey() {
        return templateKey;
    }

    public int getVersion() {
        return version;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getSchemaJson() {
        return schemaJson;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Deja de ofrecer la plantilla sin borrarla.
     *
     * <p>Es la única mutación permitida: las respuestas ya guardadas siguen refiriendo a esta
     * versión y tienen que poder validarse contra el mismo schema.
     */
    public void deactivate() {
        this.active = false;
    }
}
