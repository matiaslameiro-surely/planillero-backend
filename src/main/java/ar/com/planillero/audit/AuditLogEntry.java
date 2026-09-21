package ar.com.planillero.audit;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Fila del log de auditoría: una acción sobre el sistema, encadenada criptográficamente con la
 * anterior.
 *
 * <p>Es append-only por diseño: no expone setters más allá de los que arman el objeto una sola vez,
 * y la base rechaza cualquier {@code UPDATE}/{@code DELETE} directo (ver {@code V11}). El
 * encadenamiento lo arma {@link AuditChainService}: esta clase sólo transporta los datos ya
 * calculados.
 */
@Entity
@Table(name = "audit_logs", schema = "audit")
public class AuditLogEntry {

    @Id
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "entity_type", nullable = false, length = 80)
    private String entityType;

    @Column(name = "entity_id", length = 80)
    private String entityId;

    @Column(nullable = false, length = 80)
    private String username;

    @Column(length = 45)
    private String ip;

    @Column(name = "device_id", length = 120)
    private String deviceId;

    /**
     * Delta de la operación (argumentos y resultado), ya enmascarado, como JSON canónico
     * (claves ordenadas alfabéticamente).
     *
     * <p>Se guarda como {@code text}, no {@code jsonb} a propósito: JSONB normaliza el texto al
     * persistir (puede reordenar claves, reformatear números), y el hash de abajo se calcula sobre
     * el texto exacto de este campo. Si la columna fuera JSONB, releer la fila podría devolver un
     * texto distinto del que se hasheó, y la verificación reportaría una alteración que nunca
     * ocurrió. Con {@code text} lo que se guarda es lo que se relee, byte a byte.
     */
    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "hash_previo", nullable = false, length = 64)
    private String previousHash;

    @Column(name = "hash_actual", nullable = false, length = 64)
    private String currentHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuditLogEntry() {
        // Requerido por JPA.
    }

    public AuditLogEntry(UUID id, String eventType, String entityType, String entityId, String username, String ip,
            String deviceId, String payload, String previousHash, String currentHash, Instant createdAt) {
        this.id = id;
        this.eventType = eventType;
        this.entityType = entityType;
        this.entityId = entityId;
        this.username = username;
        this.ip = ip;
        this.deviceId = deviceId;
        this.payload = payload;
        this.previousHash = previousHash;
        this.currentHash = currentHash;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getUsername() {
        return username;
    }

    public String getIp() {
        return ip;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getPayload() {
        return payload;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public String getCurrentHash() {
        return currentHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
