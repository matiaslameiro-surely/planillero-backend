package ar.com.planillero.sync;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import ar.com.planillero.common.ApiException;

/**
 * Reserva y cierre de las claves de idempotencia.
 *
 * <p>Toda la garantía de «un solo procesamiento» vive acá, y se apoya en una sola cosa: el
 * {@code insert} de la clave. El que logra insertar procesa; los demás chocan contra la clave
 * primaria y quedan esperando. No hay {@code select} previo, porque entre un {@code select} y un
 * {@code insert} entran los otros hilos y dos instancias del backend no comparten memoria.
 *
 * <p>Cada paso corre en su <strong>propia</strong> transacción, corta y confirmada de inmediato:
 * la reserva se confirma antes de empezar a procesar el lote. Si durara todo el procesamiento, los
 * reintentos quedarían bloqueados contra el índice en vez de recibir su respuesta.
 */
@Service
public class IdempotencyService {

    private final IdempotencyKeyRepository repository;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public IdempotencyService(IdempotencyKeyRepository repository,
            PlatformTransactionManager transactionManager, Clock clock) {
        this.repository = repository;
        this.clock = clock;
        // Transacción propia y explícita, no @Transactional: los pasos se llaman entre sí dentro de
        // este mismo bean, y una llamada interna no pasa por el proxy de Spring, así que la
        // anotación quedaría sin efecto justo donde más importa.
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Resultado de intentar reservar una clave. */
    public sealed interface Reservation {

        /** La clave es nueva: hay que procesar el lote. */
        record Reserved() implements Reservation {
        }

        /** Ya se procesó: se devuelve la respuesta guardada sin volver a tocar nada. */
        record AlreadyCompleted(String responseJson) implements Reservation {
        }
    }

    /**
     * Reserva la clave para este envío, o devuelve la respuesta del envío original.
     *
     * @throws ApiException {@code 409 idempotency_key_in_progress} si otro envío con la misma clave
     *                      está en curso; {@code 409 idempotency_key_reused} si la clave ya se usó
     *                      para un cuerpo distinto o por otro usuario
     */
    public Reservation reserve(UUID key, UUID userId, String requestHash) {
        try {
            transactions.executeWithoutResult(status ->
                    repository.saveAndFlush(new IdempotencyKey(key, userId, requestHash, clock.instant())));
            return new Reservation.Reserved();
        } catch (DataIntegrityViolationException alreadyTaken) {
            // Otro envío llegó primero con esta clave. Su transacción ya terminó (el insert esperó a
            // que lo hiciera), así que la fila está y se puede leer.
            return resolveExisting(key, userId, requestHash);
        }
    }

    /**
     * Cierra la clave con la respuesta del lote.
     *
     * <p>Desde acá, todo reintento con esta clave recibe exactamente este cuerpo.
     */
    public void complete(UUID key, String responseJson) {
        transactions.executeWithoutResult(status -> {
            IdempotencyKey stored = repository.findById(key).orElseThrow(
                    () -> new IllegalStateException("La clave de idempotencia " + key + " se perdió."));
            stored.complete(responseJson, clock.instant());
            repository.save(stored);
        });
    }

    /**
     * Libera una clave reservada cuyo lote no llegó a procesarse.
     *
     * <p>Sin esto, un fallo inesperado a mitad del lote dejaría la clave {@code IN_PROGRESS} para
     * siempre y el cliente no podría reintentar nunca: recibiría {@code 409} hasta el fin de los
     * tiempos. Liberarla es lo que hace que un reintento sea posible después de un error.
     */
    public void release(UUID key) {
        transactions.executeWithoutResult(status -> repository.findById(key)
                .filter(stored -> stored.getStatus() == IdempotencyKeyStatus.IN_PROGRESS)
                .ifPresent(repository::delete));
    }

    private Reservation resolveExisting(UUID key, UUID userId, String requestHash) {
        Optional<IdempotencyKey> found = transactions.execute(status -> repository.findById(key));
        IdempotencyKey stored = found.orElseThrow(() -> ApiException.conflict(
                "idempotency_key_in_progress",
                "Hay otro envío en curso con esta clave. Reintentá en unos segundos."));

        // El mismo mensaje para "es de otro usuario" y para "el cuerpo es distinto", a propósito: un
        // cliente no tiene por qué poder distinguir si una clave existe en la cuenta de otro.
        if (!stored.getUserId().equals(userId) || !stored.getRequestHash().equals(requestHash)) {
            throw ApiException.conflict("idempotency_key_reused",
                    "Esta clave de idempotencia ya se usó para otro envío. Generá una nueva.");
        }

        if (stored.getStatus() == IdempotencyKeyStatus.IN_PROGRESS) {
            throw ApiException.conflict("idempotency_key_in_progress",
                    "Hay otro envío en curso con esta clave. Reintentá en unos segundos.");
        }

        return new Reservation.AlreadyCompleted(stored.getResponseJson());
    }

    /**
     * Huella del cuerpo del pedido.
     *
     * <p>Se compara la huella y no el cuerpo: alcanza para detectar que una clave se está reusando
     * para otro envío, y evita guardar una segunda copia de datos del expediente.
     *
     * <p>Es una comparación textual: el mismo contenido con las claves JSON en otro orden da otra
     * huella y se rechaza. Es el lado conservador del error — el cliente reintenta con el cuerpo que
     * mandó, byte por byte, así que en la práctica no ocurre.
     */
    public static String hash(String requestBody) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(requestBody.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            // SHA-256 es obligatorio en toda implementación de la plataforma Java.
            throw new IllegalStateException("SHA-256 no está disponible.", impossible);
        }
    }
}
