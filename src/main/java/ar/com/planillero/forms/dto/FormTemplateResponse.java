package ar.com.planillero.forms.dto;

import java.time.Instant;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

/**
 * Una plantilla de formulario como la ve el cliente.
 *
 * <p>El {@code schema} viaja como JSON embebido, no como texto escapado: el renderizador dinámico
 * lo consume directamente para armar los campos.
 *
 * @param id          identificador de esta versión de la plantilla
 * @param key         identificador estable, el mismo entre versiones
 * @param version     número de versión
 * @param name        nombre para mostrar
 * @param description descripción, si tiene
 * @param schema      el JSON Schema 2020-12 que define los campos y sus reglas
 * @param createdAt   cuándo se publicó esta versión
 */
public record FormTemplateResponse(
        UUID id,
        String key,
        int version,
        String name,
        String description,
        JsonNode schema,
        Instant createdAt) {
}
