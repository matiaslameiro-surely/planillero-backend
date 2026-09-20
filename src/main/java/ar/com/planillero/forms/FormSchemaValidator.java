package ar.com.planillero.forms;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.springframework.stereotype.Component;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.path.PathType;

import tools.jackson.databind.JsonNode;

/**
 * Motor de validación de formularios tipificados.
 *
 * <p>Recibe un JSON Schema y un payload de respuestas, y devuelve la lista de reglas incumplidas.
 * No tiene estado de negocio ni sabe de visitas, plantillas ni base de datos: es una función pura
 * envuelta en un bean.
 *
 * <p>Dos decisiones importantes:
 *
 * <ul>
 *   <li><strong>Dialecto fijo 2020-12.</strong> Todas las plantillas se interpretan con la misma
 *       versión del estándar. Si cada plantilla pudiera elegir el suyo, el mismo campo declarado
 *       igual podría comportarse distinto según la plantilla.
 *   <li><strong>Se acumulan todas las violaciones.</strong> Nunca corta en la primera: quien
 *       completa el formulario tiene que ver todos los campos con problema de una sola vez, no uno
 *       por viaje.
 * </ul>
 *
 * <p>Los mensajes se arman acá, en español, a partir de la palabra clave incumplida y de lo que la
 * plantilla declaraba. No se usan los de la librería, que están en inglés y cambian de texto entre
 * versiones.
 */
@Component
public class FormSchemaValidator {

    private final SchemaRegistry registry;

    /**
     * Schemas ya compilados, indexados por su texto.
     *
     * <p>Compilar un schema es caro y las plantillas son pocas e inmutables, así que la caché se
     * llena una vez y no crece: una entrada por versión de plantilla publicada.
     */
    private final Map<String, Schema> compiledSchemas = new ConcurrentHashMap<>();

    public FormSchemaValidator() {
        SchemaRegistryConfig config = SchemaRegistryConfig.builder()
                // Las rutas de error se reportan como JSON Pointer (/workedHours), que es lo que
                // entiende cualquier cliente sin tener que aprender un formato propio.
                .pathType(PathType.JSON_POINTER)
                // Explícito aunque sea el default: cortar en el primer error rompería el contrato
                // de devolver todas las violaciones.
                .failFast(false)
                .build();

        this.registry = SchemaRegistry.withDefaultDialect(
                SpecificationVersion.DRAFT_2020_12,
                builder -> builder.schemaRegistryConfig(config));
    }

    /**
     * Valida un payload contra un JSON Schema.
     *
     * @param schemaJson el schema de la plantilla, como texto
     * @param payload    las respuestas a validar
     * @return las violaciones encontradas, ordenadas por campo; vacía si el payload es válido
     */
    public List<FieldViolation> validate(String schemaJson, JsonNode payload) {
        Schema schema = compiledSchemas.computeIfAbsent(schemaJson, registry::getSchema);

        return schema.validate(payload).stream()
                .map(FormSchemaValidator::toViolation)
                .sorted(Comparator.comparing(FieldViolation::field).thenComparing(FieldViolation::rule))
                .toList();
    }

    /**
     * Valida y falla si hay violaciones.
     *
     * @throws FormValidationException con todas las violaciones encontradas
     */
    public void validateOrThrow(String schemaJson, JsonNode payload) {
        List<FieldViolation> violations = validate(schemaJson, payload);
        if (!violations.isEmpty()) {
            throw new FormValidationException(violations);
        }
    }

    /** Traduce un error de la librería al tipo propio, con mensaje en español. */
    private static FieldViolation toViolation(Error error) {
        String keyword = error.getKeyword();
        String location = error.getInstanceLocation().toString();
        String property = error.getProperty();

        // `required` y `additionalProperties` se reportan sobre el objeto contenedor, pero lo que le
        // interesa al cliente es el campo concreto: se lo agrega a la ruta.
        String field = property != null && !property.isBlank()
                ? location + "/" + property
                : location;

        return new FieldViolation(field, keyword, buildMessage(error, keyword, property));
    }

    private static String buildMessage(Error error, String keyword, String property) {
        JsonNode expected = error.getSchemaNode();

        return switch (keyword) {
            case "required" -> "El campo «" + property + "» es obligatorio.";
            case "additionalProperties" -> "El campo «" + property
                    + "» no está declarado en la plantilla y no se acepta.";
            case "type" -> "Se esperaba un valor de tipo " + describe(expected) + ".";
            case "enum" -> "El valor debe ser uno de: " + describe(expected) + ".";
            case "const" -> "El único valor aceptado es " + describe(expected) + ".";
            case "minimum" -> "El valor debe ser mayor o igual que " + expected.asString() + ".";
            case "maximum" -> "El valor debe ser menor o igual que " + expected.asString() + ".";
            case "exclusiveMinimum" -> "El valor debe ser mayor que " + expected.asString() + ".";
            case "exclusiveMaximum" -> "El valor debe ser menor que " + expected.asString() + ".";
            case "multipleOf" -> "El valor debe ser múltiplo de " + expected.asString() + ".";
            case "minLength" -> "El texto debe tener al menos " + expected.asString() + " caracteres.";
            case "maxLength" -> "El texto no puede superar los " + expected.asString() + " caracteres.";
            case "pattern" -> "El texto no cumple el formato esperado (" + expected.asString() + ").";
            case "format" -> "El valor no es un " + expected.asString() + " válido.";
            case "minItems" -> "Hay que cargar al menos " + expected.asString() + " elementos.";
            case "maxItems" -> "No se pueden cargar más de " + expected.asString() + " elementos.";
            case "uniqueItems" -> "Los elementos no pueden repetirse.";
            // Palabra clave sin mensaje propio: se usa el de la librería antes que quedarse mudo.
            default -> error.getMessage();
        };
    }

    /** Representa el valor declarado en la plantilla de forma legible: {@code "a, b, c"} o {@code "number"}. */
    private static String describe(JsonNode node) {
        if (node == null) {
            return "(sin especificar)";
        }
        if (node.isArray()) {
            return StreamSupport.stream(node.spliterator(), false)
                    .map(JsonNode::asString)
                    .collect(Collectors.joining(", "));
        }
        return node.asString();
    }

    /** Sólo para los tests: cuántos schemas distintos hay compilados en memoria. */
    int cachedSchemaCount() {
        return compiledSchemas.size();
    }
}
