package ar.com.planillero.common;

import java.util.Map;

import ar.com.planillero.forms.FormValidationException;
import ar.com.planillero.forms.dto.ValidationErrorResponse;
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
}
