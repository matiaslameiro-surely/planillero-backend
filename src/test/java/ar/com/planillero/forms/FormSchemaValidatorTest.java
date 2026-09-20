package ar.com.planillero.forms;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pruebas del motor de validación.
 *
 * <p>Sin Spring y sin base: el motor es una función pura y se prueba como tal. Hay un test por tipo
 * de regla porque lo que interesa no es sólo que rechace, sino <strong>qué</strong> reporta: la ruta
 * del campo y la palabra clave incumplida son contrato con el cliente.
 */
class FormSchemaValidatorTest {

    /** Plantilla ficticia que ejercita todos los tipos de regla de una sola vez. */
    private static final String SCHEMA = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "additionalProperties": false,
              "required": ["workedHours", "taskType", "observations"],
              "properties": {
                "workedHours": { "type": "number", "minimum": 0, "maximum": 24 },
                "taskType":    { "type": "string", "enum": ["PREVENTIVO", "CORRECTIVO"] },
                "observations":{ "type": "string", "maxLength": 20 },
                "serialNumber":{ "type": "string", "pattern": "^[A-Z]{3}-[0-9]{4}$" }
              }
            }
            """;

    private final FormSchemaValidator validator = new FormSchemaValidator();
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    @DisplayName("un payload que cumple el schema no produce ninguna violación")
    void payloadValidoNoTieneViolaciones() {
        List<FieldViolation> violations = validate("""
                {
                  "workedHours": 8,
                  "taskType": "PREVENTIVO",
                  "observations": "Sin novedades",
                  "serialNumber": "ABC-1234"
                }
                """);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("un campo requerido ausente se reporta sobre el campo, no sobre el objeto")
    void campoRequeridoAusente() {
        List<FieldViolation> violations = validate("""
                { "workedHours": 8, "taskType": "PREVENTIVO" }
                """);

        assertThat(violations).singleElement().satisfies(violation -> {
            assertThat(violation.field()).isEqualTo("/observations");
            assertThat(violation.rule()).isEqualTo("required");
            assertThat(violation.message()).contains("obligatorio");
        });
    }

    @Test
    @DisplayName("un tipo incorrecto indica qué tipo se esperaba")
    void tipoIncorrecto() {
        List<FieldViolation> violations = validate("""
                { "workedHours": "ocho", "taskType": "PREVENTIVO", "observations": "ok" }
                """);

        assertThat(violations).singleElement().satisfies(violation -> {
            assertThat(violation.field()).isEqualTo("/workedHours");
            assertThat(violation.rule()).isEqualTo("type");
            assertThat(violation.message()).contains("number");
        });
    }

    @Test
    @DisplayName("un valor por encima del máximo se reporta con la regla maximum")
    void valorFueraDeRango() {
        List<FieldViolation> violations = validate("""
                { "workedHours": 30, "taskType": "PREVENTIVO", "observations": "ok" }
                """);

        assertThat(violations).singleElement().satisfies(violation -> {
            assertThat(violation.field()).isEqualTo("/workedHours");
            assertThat(violation.rule()).isEqualTo("maximum");
            assertThat(violation.message()).contains("24");
        });
    }

    @Test
    @DisplayName("un valor por debajo del mínimo se reporta con la regla minimum")
    void valorPorDebajoDelMinimo() {
        List<FieldViolation> violations = validate("""
                { "workedHours": -1, "taskType": "PREVENTIVO", "observations": "ok" }
                """);

        assertThat(violations).singleElement().satisfies(violation -> {
            assertThat(violation.field()).isEqualTo("/workedHours");
            assertThat(violation.rule()).isEqualTo("minimum");
        });
    }

    @Test
    @DisplayName("un string que no cumple el patrón se reporta con la regla pattern")
    void patronIncumplido() {
        List<FieldViolation> violations = validate("""
                {
                  "workedHours": 8, "taskType": "PREVENTIVO", "observations": "ok",
                  "serialNumber": "abc-1"
                }
                """);

        assertThat(violations).singleElement().satisfies(violation -> {
            assertThat(violation.field()).isEqualTo("/serialNumber");
            assertThat(violation.rule()).isEqualTo("pattern");
        });
    }

    @Test
    @DisplayName("un string más largo que maxLength se reporta con la regla maxLength")
    void largoExcedido() {
        List<FieldViolation> violations = validate("""
                {
                  "workedHours": 8, "taskType": "PREVENTIVO",
                  "observations": "un texto bastante más largo que el máximo declarado"
                }
                """);

        assertThat(violations).singleElement().satisfies(violation -> {
            assertThat(violation.field()).isEqualTo("/observations");
            assertThat(violation.rule()).isEqualTo("maxLength");
            assertThat(violation.message()).contains("20");
        });
    }

    @Test
    @DisplayName("un valor fuera del enum enumera los valores aceptados")
    void valorFueraDelEnum() {
        List<FieldViolation> violations = validate("""
                { "workedHours": 8, "taskType": "URGENTE", "observations": "ok" }
                """);

        assertThat(violations).singleElement().satisfies(violation -> {
            assertThat(violation.field()).isEqualTo("/taskType");
            assertThat(violation.rule()).isEqualTo("enum");
            assertThat(violation.message()).contains("PREVENTIVO").contains("CORRECTIVO");
        });
    }

    @Test
    @DisplayName("una propiedad no declarada se rechaza cuando additionalProperties es false")
    void propiedadNoDeclarada() {
        List<FieldViolation> violations = validate("""
                {
                  "workedHours": 8, "taskType": "PREVENTIVO", "observations": "ok",
                  "campoInventado": "algo"
                }
                """);

        assertThat(violations).singleElement().satisfies(violation -> {
            assertThat(violation.field()).isEqualTo("/campoInventado");
            assertThat(violation.rule()).isEqualTo("additionalProperties");
        });
    }

    @Test
    @DisplayName("un payload con tres errores devuelve las tres violaciones, no la primera")
    void seAcumulanTodasLasViolaciones() {
        List<FieldViolation> violations = validate("""
                { "workedHours": 99, "taskType": "URGENTE" }
                """);

        assertThat(violations).hasSize(3);
        assertThat(violations).extracting(FieldViolation::rule)
                .containsExactlyInAnyOrder("maximum", "enum", "required");
        assertThat(violations).extracting(FieldViolation::field)
                .containsExactlyInAnyOrder("/workedHours", "/taskType", "/observations");
    }

    @Test
    @DisplayName("validar dos veces el mismo schema lo compila una sola vez")
    void elSchemaSeCompilaUnaSolaVez() {
        String payload = """
                { "workedHours": 8, "taskType": "PREVENTIVO", "observations": "ok" }
                """;

        validate(payload);
        validate(payload);

        assertThat(validator.cachedSchemaCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("el motor no interpreta el contenido: un texto con SQL adentro es un texto válido")
    void contenidoConSqlEsUnTextoComoCualquierOtro() {
        List<FieldViolation> violations = validate("""
                {
                  "workedHours": 8, "taskType": "PREVENTIVO",
                  "observations": "'; drop table x"
                }
                """);

        assertThat(violations).isEmpty();
    }

    private List<FieldViolation> validate(String payload) {
        JsonNode node = mapper.readTree(payload);
        return validator.validate(SCHEMA, node);
    }
}
