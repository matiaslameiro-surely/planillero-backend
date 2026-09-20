package ar.com.planillero.visits;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso al formulario de las visitas. Consultas derivadas y parametrizadas: ningún valor arma SQL. */
public interface VisitFormRecordRepository extends JpaRepository<VisitFormRecord, UUID> {
}
