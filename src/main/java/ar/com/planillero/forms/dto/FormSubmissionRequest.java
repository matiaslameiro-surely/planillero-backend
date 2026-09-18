package ar.com.planillero.forms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import tools.jackson.databind.JsonNode;

/**
 * Envío del formulario de una visita.
 *
 * <p>Sólo se valida acá la forma del sobre: que venga la clave de plantilla y que haya respuestas.
 * El contenido de {@code responses} no se valida con anotaciones —no se puede, es distinto en cada
 * plantilla— sino contra el JSON Schema de la plantilla indicada.
 *
 * @param templateKey     clave de la plantilla contra la que validar
 * @param templateVersion versión puntual; si no viene, se usa la última versión vigente
 * @param responses       las respuestas, con la forma que declare el schema de la plantilla
 */
public record FormSubmissionRequest(
        @NotBlank(message = "es obligatorio") String templateKey,
        @Positive(message = "debe ser un número de versión válido") Integer templateVersion,
        @NotNull(message = "es obligatorio") JsonNode responses) {
}
