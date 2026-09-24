package ar.com.planillero.audit.dto;

import java.time.Instant;
import java.util.UUID;

import ar.com.planillero.audit.AuditLogEntry;

/**
 * Fila de auditoría tal como la consume el backoffice.
 *
 * <p>{@code entityCode} es el código de la visita ({@code V-1001}) cuando {@code entityType} es
 * {@code VISIT}. No está en la tabla: se resuelve al leer, así las filas append-only y sus hashes no
 * cambian (PLAN-46).
 */
public record AuditLogDto(
        UUID id,
        String eventType,
        String entityType,
        String entityId,
        String username,
        String ip,
        String deviceId,
        String payload,
        Instant createdAt,
        String entityCode) {

    public static AuditLogDto from(AuditLogEntry entry) {
        return from(entry, null);
    }

    public static AuditLogDto from(AuditLogEntry entry, String entityCode) {
        return new AuditLogDto(entry.getId(), entry.getEventType(), entry.getEntityType(), entry.getEntityId(),
                entry.getUsername(), entry.getIp(), entry.getDeviceId(), entry.getPayload(), entry.getCreatedAt(),
                entityCode);
    }
}
