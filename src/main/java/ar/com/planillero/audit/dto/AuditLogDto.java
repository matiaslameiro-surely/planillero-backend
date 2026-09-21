package ar.com.planillero.audit.dto;

import java.time.Instant;
import java.util.UUID;

import ar.com.planillero.audit.AuditLogEntry;

/** Fila de auditoría tal como la consume el backoffice. */
public record AuditLogDto(
        UUID id,
        String eventType,
        String entityType,
        String entityId,
        String username,
        String ip,
        String deviceId,
        String payload,
        Instant createdAt) {

    public static AuditLogDto from(AuditLogEntry entry) {
        return new AuditLogDto(entry.getId(), entry.getEventType(), entry.getEntityType(), entry.getEntityId(),
                entry.getUsername(), entry.getIp(), entry.getDeviceId(), entry.getPayload(), entry.getCreatedAt());
    }
}
