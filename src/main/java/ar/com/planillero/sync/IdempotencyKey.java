package ar.com.planillero.sync;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/**
 * Una clave de idempotencia y el envío que representa.
 *
 * <p>Es el registro de «este lote ya se procesó y devolvió esto». Mientras está
 * {@link IdempotencyKeyStatus#IN_PROGRESS} no hay respuesta que devolver; cuando se cierra, la
 * respuesta guardada es la única verdad: un reintento recibe exactamente lo mismo que el primer
 * envío, aunque los datos hayan cambiado después.
 *
 * <p>Implementa {@link Persistable} por un motivo del que depende toda la garantía: el
 * identificador lo trae el cliente, así que Spring Data da la entidad por existente y guardarla haría
 * un {@code merge} —un {@code select} y después un {@code update}—. Una clave repetida
 * <strong>pisaría</strong> la anterior en vez de chocar contra la clave primaria, y el segundo envío
 * se procesaría como si fuera el primero. Diciendo explícitamente que es nueva, la escritura es un
 * {@code insert} y la base puede rechazar el duplicado, que es justamente lo que se le pide.
 */
@Entity
@Table(name = "idempotency_keys", schema = "sync")
public class IdempotencyKey implements Persistable<UUID> {

    @Id
    @Column(name = "idempotency_key")
    private UUID idempotencyKey;

    /** Quién creó la clave. Sólo esa persona puede recuperar su respuesta. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** SHA-256 en hexadecimal del cuerpo del pedido. */
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IdempotencyKeyStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_json")
    private String responseJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** Si esta instancia todavía no llegó a la base. No se persiste. */
    @Transient
    private boolean nueva;

    protected IdempotencyKey() {
        // Requerido por JPA. Una instancia que arma Hibernate viene de la base, así que no es nueva.
    }

    public IdempotencyKey(UUID idempotencyKey, UUID userId, String requestHash, Instant createdAt) {
        this.idempotencyKey = idempotencyKey;
        this.userId = userId;
        this.requestHash = requestHash;
        this.status = IdempotencyKeyStatus.IN_PROGRESS;
        this.createdAt = createdAt;
        this.nueva = true;
    }

    @Override
    public UUID getId() {
        return idempotencyKey;
    }

    @Override
    public boolean isNew() {
        return nueva;
    }

    @PostPersist
    @PostLoad
    void yaEstaEnLaBase() {
        this.nueva = false;
    }

    /** Cierra la clave con la respuesta que hay que devolver en todo reintento posterior. */
    public void complete(String responseJson, Instant completedAt) {
        this.responseJson = responseJson;
        this.completedAt = completedAt;
        this.status = IdempotencyKeyStatus.COMPLETED;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public IdempotencyKeyStatus getStatus() {
        return status;
    }

    public String getResponseJson() {
        return responseJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
