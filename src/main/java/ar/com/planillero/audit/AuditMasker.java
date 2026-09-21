package ar.com.planillero.audit;

import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Enmascara valores sensibles dentro de un árbol JSON antes de que llegue a {@code audit.audit_logs}
 * (OWASP A09: los logs no pueden ser una segunda fuga de credenciales).
 *
 * <p>El criterio es el nombre de la clave, no su valor: cualquier clave cuyo nombre contenga
 * {@code password}, {@code token}, {@code secret} o {@code dni} (sin importar mayúsculas) se
 * reemplaza por {@code "***"}. Es deliberadamente amplio: preferimos enmascarar de más a filtrar
 * datos sensibles con nombres que hoy no imaginamos.
 */
@Component
public class AuditMasker {

    private static final String MASK = "***";
    private static final Pattern SENSITIVE_KEY = Pattern.compile(
            "password|token|secret|dni", Pattern.CASE_INSENSITIVE);

    /** Enmascara en el lugar y devuelve el mismo nodo, para poder encadenar la llamada. */
    public JsonNode mask(JsonNode node) {
        if (node instanceof ObjectNode object) {
            Iterator<Map.Entry<String, JsonNode>> fields = object.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (SENSITIVE_KEY.matcher(field.getKey()).find()) {
                    object.put(field.getKey(), MASK);
                } else {
                    mask(field.getValue());
                }
            }
        } else if (node instanceof ArrayNode array) {
            for (JsonNode element : array) {
                mask(element);
            }
        }
        return node;
    }
}
