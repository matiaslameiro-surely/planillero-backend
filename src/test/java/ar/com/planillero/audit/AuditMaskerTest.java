package ar.com.planillero.audit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class AuditMaskerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AuditMasker masker = new AuditMasker();

    @Test
    void masksTopLevelSensitiveFields() {
        JsonNode node = mapper.valueToTree(java.util.Map.of(
                "username", "operador.demo",
                "password", "s3cr3t!",
                "accessToken", "abc.def.ghi"));

        JsonNode masked = masker.mask(node);

        assertThat(masked.get("username").asString()).isEqualTo("operador.demo");
        assertThat(masked.get("password").asString()).isEqualTo("***");
        assertThat(masked.get("accessToken").asString()).isEqualTo("***");
    }

    @Test
    void masksNestedFieldsInsideObjectsAndArrays() {
        JsonNode node = mapper.valueToTree(java.util.Map.of(
                "args", java.util.Map.of("dniOperador", "30111222", "note", "sin novedad"),
                "items", java.util.List.of(
                        java.util.Map.of("secretCode", "x1"),
                        java.util.Map.of("value", 1))));

        JsonNode masked = masker.mask(node);

        assertThat(masked.get("args").get("dniOperador").asString()).isEqualTo("***");
        assertThat(masked.get("args").get("note").asString()).isEqualTo("sin novedad");
        assertThat(masked.get("items").get(0).get("secretCode").asString()).isEqualTo("***");
        assertThat(masked.get("items").get(1).get("value").asInt()).isEqualTo(1);
    }

    @Test
    void leavesPayloadWithoutSensitiveKeysUntouched() {
        JsonNode node = mapper.valueToTree(java.util.Map.of("visitCode", "T-001", "status", "IN_PROGRESS"));

        JsonNode masked = masker.mask(node);

        assertThat(masked.get("visitCode").asString()).isEqualTo("T-001");
        assertThat(masked.get("status").asString()).isEqualTo("IN_PROGRESS");
    }
}
