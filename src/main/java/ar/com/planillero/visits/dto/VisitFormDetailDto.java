package ar.com.planillero.visits.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import ar.com.planillero.planning.VisitStatus;
import ar.com.planillero.planning.VisitUrgency;
import tools.jackson.databind.JsonNode;

/**
 * La visita con su formulario cargado, tal como la consume el visor del expediente digital.
 *
 * <p>Combina los datos de la visita (planificación) con los del formulario guardado en
 * {@code visits.visits} ({@code form_template_id}, {@code responses_json},
 * {@code form_submitted_at}). Los campos de formulario son {@code null} cuando la visita todavía no
 * cargó ninguna planilla.
 *
 * @param id              identificador de la visita
 * @param code            código legible de la visita
 * @param address         dirección
 * @param latitude        latitud
 * @param longitude       longitud
 * @param jurisdiction    jurisdicción
 * @param status          estado del ciclo de vida
 * @param urgency         urgencia
 * @param createdAt       cuándo se creó la visita
 * @param formTemplateId  versión puntual de plantilla contra la que se validaron las respuestas
 * @param templateKey     clave estable de la plantilla, o {@code null} si no hay formulario
 * @param templateVersion número de versión de la plantilla, o {@code null}
 * @param templateName    nombre para mostrar de la plantilla, o {@code null}
 * @param responses       el JSON exacto que envió el operador, o {@code null}
 * @param submittedAt     cuándo se cargó el formulario, o {@code null}
 */
public record VisitFormDetailDto(
        UUID id,
        String code,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String jurisdiction,
        VisitStatus status,
        VisitUrgency urgency,
        Instant createdAt,
        UUID formTemplateId,
        String templateKey,
        Integer templateVersion,
        String templateName,
        JsonNode responses,
        Instant submittedAt) {
}