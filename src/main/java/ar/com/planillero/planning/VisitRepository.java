package ar.com.planillero.planning;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso a las visitas persistidas. */
public interface VisitRepository extends JpaRepository<Visit, UUID> {

    List<Visit> findByJurisdictionOrderByCodeAsc(String jurisdiction);

    /** El código es único ({@code visits.code unique}): lo usa la auditoría para verificar por código. */
    Optional<Visit> findByCode(String code);

    /**
     * Lee la visita bloqueando su fila hasta el fin de la transacción.
     *
     * <p>Sirve para iniciar una visita: si dos pedidos llegan a la vez, el segundo espera, ve la visita
     * ya iniciada y recibe un 409, en vez de que los dos la inicien y uno pise los datos del otro.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Visit v where v.id = :id")
    Optional<Visit> findByIdForUpdate(@Param("id") UUID id);
}
