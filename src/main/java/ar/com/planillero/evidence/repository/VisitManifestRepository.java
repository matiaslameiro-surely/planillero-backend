package ar.com.planillero.evidence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ar.com.planillero.evidence.model.VisitManifest;

@Repository
public interface VisitManifestRepository extends JpaRepository<VisitManifest, UUID> {

    Optional<VisitManifest> findFirstByVisitIdOrderByCreatedAtDesc(UUID visitId);

    List<VisitManifest> findByVisitIdOrderByCreatedAtDesc(UUID visitId);
}
