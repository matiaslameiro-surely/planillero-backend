package ar.com.planillero.common;

import java.util.Map;

import ar.com.planillero.forms.FormValidationException;
import ar.com.planillero.forms.dto.ValidationErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
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

    /**
     * Formulario que no cumple el schema de su plantilla.
     *
     * <p>Rompe a propósito la forma de dos campos: suma {@code violations} con <strong>todas</strong>
     * las reglas incumplidas. Un formulario dinámico necesita marcar cada campo con problema de una
     * sola vez, y un único mensaje no alcanza para eso.
     */
    @ExceptionHandler(FormValidationException.class)
    public ResponseEntity<ValidationErrorResponse> handleFormValidation(FormValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ValidationErrorResponse.of(ex.getViolations()));
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

    /**
     * Un parámetro que no se puede convertir al tipo esperado (por ejemplo, un UUID mal formado). El
     * mensaje de la excepción es técnico y en inglés: se responde uno propio (PLAN-46).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "invalid_parameter",
                "message", "El parámetro «" + ex.getName() + "» tiene un formato inválido."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "bad_request",
                "message", ex.getMessage()));
    }
}
