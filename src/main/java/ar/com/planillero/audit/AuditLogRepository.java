package ar.com.planillero.audit;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acceso a {@code audit.audit_logs}.
 *
 * <p>Deliberadamente no expone ningún método de actualización o borrado: la tabla es append-only y
 * lo único que se hace acá es {@code save} (insert) y lectura. La inmutabilidad real la impone la
 * base (trigger de {@code V11}); no exponer el método es la primera línea de defensa.
 */
public interface AuditLogRepository extends JpaRepository<AuditLogEntry, UUID> {

    /**
     * Última fila de la cadena, bloqueada hasta el fin de la transacción.
     *
     * <p>Es lo que serializa el encadenamiento: dos escrituras concurrentes no pueden calcular su
     * {@code hash_previo} contra la misma fila. La segunda espera a que la primera confirme, y su
     * {@code SELECT} (repetido tras liberarse el lock) ve ya la fila nueva. Mismo patrón que
     * {@link ar.com.planillero.planning.VisitRepository#findByIdForUpdate}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AuditLogEntry> findFirstByOrderByCreatedAtDesc();

    /** Cadena completa (o de una entidad puntual) en orden cronológico, para verificarla. */
    @Query("select a from AuditLogEntry a "
            + "where (:entityId is null or a.entityId = :entityId) "
            + "order by a.createdAt asc")
    java.util.List<AuditLogEntry> findChain(@Param("entityId") String entityId);

    // Los filtros de fecha van con COALESCE, no con "(:from is null or ...)": ese patrón deja un
    // "$n is null" sin ningún operando tipado al lado, y Postgres no puede inferir el tipo del
    // parámetro para un Instant nulo (sí puede para String). COALESCE ata el parámetro a una
    // columna con tipo conocido en todas sus apariciones.
    @Query("select a from AuditLogEntry a "
            + "where (:eventType is null or a.eventType = :eventType) "
            + "and (:username is null or a.username = :username) "
            + "and a.createdAt >= coalesce(:from, a.createdAt) "
            + "and a.createdAt <= coalesce(:to, a.createdAt) "
            + "order by a.createdAt desc")
    Page<AuditLogEntry> search(
            @Param("eventType") String eventType,
            @Param("username") String username,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);
}
