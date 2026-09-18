package ar.com.planillero.forms;

/**
 * Una regla del schema que el payload no cumplió.
 *
 * <p>Es el tipo con el que el backend habla de errores de formulario. Existe para que ni el resto
 * del código ni el cliente queden atados a la API de la librería de validación: si mañana se cambia
 * de librería, cambia {@link FormSchemaValidator} y nada más.
 *
 * @param field   ruta JSON Pointer del campo que falló, por ejemplo {@code /workedHours}. Vacía
 *                cuando la violación es del objeto entero (por ejemplo, un campo requerido ausente
 *                se reporta sobre el objeto que lo debía contener).
 * @param rule    palabra clave de JSON Schema incumplida ({@code required}, {@code type},
 *                {@code maximum}, {@code pattern}, {@code enum}…). Es estable: el cliente puede
 *                decidir con esto sin leer el mensaje.
 * @param message explicación legible, en español, para mostrarle a quien completa el formulario.
 */
public record FieldViolation(String field, String rule, String message) {
}
