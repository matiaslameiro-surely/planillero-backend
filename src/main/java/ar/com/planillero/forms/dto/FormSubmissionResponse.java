package ar.com.planillero.forms.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Confirmación de que el formulario se validó y quedó guardado.
 *
 * <p>Devuelve la versión exacta de plantilla contra la que se validó: es el dato que después
 * permite releer las respuestas sabiendo qué reglas las aceptaron.
 *
 * @param visitId         visita a la que se le cargó el formulario
 * @param templateKey     clave de la plantilla usada
 * @param templateVersion versión exacta de esa plantilla
 * @param submittedAt     momento en que se registró el envío
 */
public record FormSubmissionResponse(
        UUID visitId,
        String templateKey,
        int templateVersion,
        Instant submittedAt) {
}
