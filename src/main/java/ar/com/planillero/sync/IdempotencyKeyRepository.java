package ar.com.planillero.sync;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a las claves de idempotencia.
 *
 * <p>Consultas derivadas del nombre del método: las arma Spring Data como sentencias parametrizadas,
 * así que ningún valor de entrada forma parte del texto del SQL (OWASP A03).
 */
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {
}
