package ar.com.planillero.forms;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso al catálogo de plantillas de formulario.
 *
 * <p>Todas las consultas son derivadas del nombre del método: las arma Spring Data como sentencias
 * parametrizadas, así que ningún valor de entrada llega a formar parte del texto del SQL
 * (OWASP A03).
 */
public interface FormTemplateRepository extends JpaRepository<FormTemplate, UUID> {

    /** Plantillas vigentes, con un orden estable para que el cliente vea siempre la misma lista. */
    List<FormTemplate> findByActiveTrueOrderByTemplateKeyAscVersionDesc();

    /** La última versión vigente de una clave, que es la que se le ofrece a quien completa hoy. */
    Optional<FormTemplate> findFirstByTemplateKeyAndActiveTrueOrderByVersionDesc(String templateKey);

    /** Una versión puntual, vigente o no: las respuestas viejas se validan contra su propia versión. */
    Optional<FormTemplate> findByTemplateKeyAndVersion(String templateKey, int version);
}
