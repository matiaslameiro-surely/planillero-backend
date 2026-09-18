package ar.com.planillero.visits;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a visitas. Consultas derivadas y parametrizadas: ningún valor arma texto de SQL. */
public interface VisitRepository extends JpaRepository<Visit, UUID> {
}
