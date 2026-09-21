package ar.com.planillero.sync;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acceso a las claves de idempotencia.
 *
 * <p>Consultas derivadas del nombre del método: las arma Spring Data como sentencias parametrizadas,
 * así que ningún valor de entrada forma parte del texto del SQL (OWASP A03).
 */
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {

    /**
     * Retoma una reserva que quedó abandonada, y devuelve {@code 1} si la tomó.
     *
     * <p>Una reserva queda huérfana cuando el proceso muere entre que la clave se reserva y que se
     * guarda la respuesta. Sin esto, esa clave se queda {@code IN_PROGRESS} para siempre y el
     * dispositivo recibe {@code 409} en cada reintento: su lote no se aplica nunca.
     *
     * <p>Todo el filtro va en el {@code where}, así que la toma es atómica: de varios procesos que
     * intenten retomarla al mismo tiempo, exactamente uno actualiza la fila y los demás reciben
     * {@code 0}. Comprobar la antigüedad en Java y después actualizar volvería a abrir la carrera
     * que esta tabla existe para cerrar.
     *
     * <p>Sólo retoma el <strong>mismo</strong> envío: el usuario y la huella del cuerpo tienen que
     * coincidir. Una reserva vencida habilita a terminar lo que quedó a medias, no a que la clave
     * sirva para cualquier otra cosa — si no, la protección contra claves reutilizadas caducaría
     * sola a los dos minutos. Por eso van en el {@code where} y no se reescriben.
     */
    @Modifying
    @Query(value = """
            update sync.idempotency_keys
               set created_at = :now
             where idempotency_key = :key
               and status = 'IN_PROGRESS'
               and user_id = :userId
               and request_hash = :requestHash
               and created_at < :vencidaAntesDe
            """, nativeQuery = true)
    int takeOverAbandoned(
            @Param("key") UUID key,
            @Param("userId") UUID userId,
            @Param("requestHash") String requestHash,
            @Param("now") Instant now,
            @Param("vencidaAntesDe") Instant vencidaAntesDe);
}
