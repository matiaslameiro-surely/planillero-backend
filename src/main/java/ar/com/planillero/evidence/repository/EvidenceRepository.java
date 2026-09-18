package ar.com.planillero.evidence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ar.com.planillero.evidence.model.Evidence;

@Repository
public interface EvidenceRepository extends JpaRepository<Evidence, UUID> {

    List<Evidence> findByVisitIdOrderByCapturedAtAsc(UUID visitId);

    Optional<Evidence> findByIdAndVisitId(UUID id, UUID visitId);

    Optional<Evidence> findByStoragePath(String storagePath);
}
