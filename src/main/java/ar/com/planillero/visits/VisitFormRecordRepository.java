package ar.com.planillero.visits;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso al formulario de las visitas. Consultas derivadas y parametrizadas: ningún valor arma SQL. */
public interface VisitFormRecordRepository extends JpaRepository<VisitFormRecord, UUID> {

    /**
     * La visita cuyo formulario trajo una operación de sincronización, si esa operación ya se aplicó.
     *
     * <p>Es la consulta que convierte un reintento en una respuesta en vez de en un acta duplicada.
     */
    Optional<VisitFormRecord> findBySyncOperationId(UUID syncOperationId);

    /**
     * Lee la visita bloqueando su fila hasta el fin de la transacción.
     *
     * <p>Es lo que hace atómico el «¿ya se aplicó esta operación?» seguido del guardado. El índice
     * único de {@code sync_operation_id} no alcanza para esto: el formulario se guarda con un
     * {@code update} sobre una fila que ya existe, así que veinte hilos escribiendo el mismo
     * identificador sobre la misma visita no violan ningún índice — se pisan uno al otro sin que
     * nada se queje. Con la fila bloqueada, el segundo hilo espera, ve la operación ya aplicada y
     * responde duplicado.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from VisitFormRecord v where v.id = :id")
    Optional<VisitFormRecord> findByIdForUpdate(@Param("id") UUID id);
}
