package ar.com.planillero.common;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Convierte las excepciones de negocio en un cuerpo JSON consistente.
 *
 * <p>La forma es siempre la misma —{@code error} y {@code message}— para que los clientes tengan un
 * solo camino de manejo de errores.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, String>> handleApiException(ApiException ex) {
        return ResponseEntity.status(ex.getStatus()).body(Map.of(
                "error", ex.getCode(),
                "message", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        String detalle = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("El pedido no es válido.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "invalid_request",
                "message", detalle));
    }

    @ExceptionHandler(ar.com.planillero.evidence.service.IntegrityMismatchException.class)
    public ResponseEntity<Map<String, String>> handleIntegrityMismatch(
            ar.com.planillero.evidence.service.IntegrityMismatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "integrity_mismatch",
                "message", ex.getMessage()));
    }

    @ExceptionHandler(ar.com.planillero.evidence.storage.WormPolicyViolationException.class)
    public ResponseEntity<Map<String, String>> handleWormViolation(
            ar.com.planillero.evidence.storage.WormPolicyViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "worm_policy_violation",
                "message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "bad_request",
                "message", ex.getMessage()));
    }
}
