package ar.com.planillero.forms.dto;

import java.util.List;

import ar.com.planillero.forms.FieldViolation;

/**
 * Cuerpo del {@code 400} cuando el formulario no cumple su schema.
 *
 * <p>Mantiene la forma común de errores de la API ({@code error} + {@code message}) y le suma la
 * lista completa de campos con problema, para que el cliente los pueda marcar todos juntos.
 *
 * @param error      código estable, siempre {@code form_validation_failed}
 * @param message    resumen legible
 * @param violations una entrada por regla incumplida
 */
public record ValidationErrorResponse(String error, String message, List<FieldViolation> violations) {

    public static ValidationErrorResponse of(List<FieldViolation> violations) {
        String message = violations.size() == 1
                ? "El formulario tiene 1 campo con problemas."
                : "El formulario tiene " + violations.size() + " campos con problemas.";
        return new ValidationErrorResponse("form_validation_failed", message, violations);
    }
}
