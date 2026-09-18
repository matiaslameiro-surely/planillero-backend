package ar.com.planillero.planning;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a las visitas persistidas. */
public interface VisitRepository extends JpaRepository<Visit, UUID> {

    List<Visit> findByJurisdictionOrderByCodeAsc(String jurisdiction);
}