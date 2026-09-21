package ar.com.planillero.sync;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.com.planillero.common.ApiException;
import ar.com.planillero.sync.dto.SyncBatchRequest;
import ar.com.planillero.sync.dto.SyncBatchResponse;
import jakarta.validation.Valid;

/**
 * Sincronización de lo que el operador cargó sin conexión.
 *
 * <p>Es el único endpoint del sistema que exige {@code Idempotency-Key}, y lo exige porque es el
 * único al que el cliente le va a reintentar a ciegas: cuando se corta la red no hay forma de saber
 * si el servidor recibió el envío o no. Sin la clave, el reintento duplicaría actas de un expediente
 * judicial.
 */
@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {

    /** Header por el que viaja la clave del envío. El nombre es el de uso corriente en la industria. */
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    /**
     * Aplica un lote de operaciones hechas sin conexión.
     *
     * <p>Responde {@code 200} con el desenlace de cada operación. Que una operación falle no hace
     * fallar el lote: el código HTTP dice si el envío se procesó, no si todos los datos eran válidos.
     */
    @PostMapping("/batch")
    @PreAuthorize("hasAnyRole('OPERATOR', 'SUPERVISOR', 'ADMINISTRATOR')")
    public SyncBatchResponse sync(
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody SyncBatchRequest request,
            Authentication authentication) {
        return syncService.process(
                requireUuidV4(idempotencyKey), request, authentication.getName());
    }

    /**
     * Exige que la clave sea un UUID versión 4.
     *
     * <p>No es formalismo: la garantía de no duplicación se apoya en que dos envíos distintos nunca
     * compartan clave. Un contador o un texto libre («1», «lote-de-hoy») colisiona entre dispositivos,
     * y la colisión no se vería como un error sino como un reintento — le devolvería a un operador la
     * respuesta del lote de otro. Un UUID v4 es aleatorio y no colisiona en la práctica.
     */
    private static UUID requireUuidV4(String header) {
        if (header == null || header.isBlank()) {
            throw ApiException.badRequest("idempotency_key_required",
                    "Falta el header Idempotency-Key.");
        }

        UUID key;
        try {
            key = UUID.fromString(header.trim());
        } catch (IllegalArgumentException malformed) {
            throw ApiException.badRequest("idempotency_key_invalid",
                    "El header Idempotency-Key tiene que ser un UUID versión 4.");
        }

        if (!esUuidV4(key)) {
            throw ApiException.badRequest("idempotency_key_invalid",
                    "El header Idempotency-Key tiene que ser un UUID versión 4.");
        }
        return key;
    }

    /**
     * Un UUID versión 4 de verdad: versión 4 <em>y</em> variante RFC 4122.
     *
     * <p>La variante no es un detalle de formato. {@code 00000000-0000-4000-0000-000000000000} tiene
     * versión 4 para Java y no lo generó ningún generador aleatorio: es el tipo de valor que aparece
     * cuando alguien arma la clave a mano, y una clave previsible puede colisionar con la de otro
     * dispositivo. Una colisión no se vería como un error sino como un reintento, y le devolvería a
     * un operador la respuesta del lote de otro.
     */
    static boolean esUuidV4(UUID value) {
        return value.version() == 4 && value.variant() == 2;
    }
}
