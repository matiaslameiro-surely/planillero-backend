package ar.com.planillero.supervision;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repositorio de persistencia de turnos de operadores.
 */
@Repository
public interface OperatorShiftRepository extends JpaRepository<OperatorShift, UUID> {

    Optional<OperatorShift> findByOperatorIdAndShiftDate(UUID operatorId, LocalDate shiftDate);

    @Query("SELECT s FROM OperatorShift s JOIN FETCH s.operator WHERE s.shiftDate = :shiftDate")
    List<OperatorShift> findByShiftDateWithOperator(@Param("shiftDate") LocalDate shiftDate);

    @Query("SELECT s FROM OperatorShift s JOIN FETCH s.operator WHERE s.jurisdiction = :jurisdiction AND s.shiftDate = :shiftDate")
    List<OperatorShift> findByJurisdictionAndShiftDateWithOperator(
            @Param("jurisdiction") String jurisdiction,
            @Param("shiftDate") LocalDate shiftDate);

    long countByJurisdictionAndShiftDateAndStatus(String jurisdiction, LocalDate shiftDate, ShiftStatus status);

    long countByShiftDateAndStatus(LocalDate shiftDate, ShiftStatus status);
}
