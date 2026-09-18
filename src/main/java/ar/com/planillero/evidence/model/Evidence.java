package ar.com.planillero.evidence.model;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad que representa un registro de evidencia digital pericial (fotografía o firma ológrafa).
 */
@Entity
@Table(name = "evidences", schema = "visits")
public class Evidence {

    @Id
    private UUID id;

    @Column(name = "visit_id", nullable = false)
    private UUID visitId;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false, length = 30)
    private EvidenceType evidenceType;

    @Column(name = "storage_path", nullable = false, unique = true, length = 255)
    private String storagePath;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "sha256_hash", nullable = false, length = 64)
    private String sha256Hash;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    protected Evidence() {
        // Constructor por defecto requerido por JPA.
    }

    public Evidence(
            UUID id,
            UUID visitId,
            EvidenceType evidenceType,
            String storagePath,
            String fileName,
            String contentType,
            long fileSize,
            String sha256Hash,
            Instant capturedAt,
            String metadata) {
        this.id = id != null ? id : UUID.randomUUID();
        this.visitId = visitId;
        this.evidenceType = evidenceType;
        this.storagePath = storagePath;
        this.fileName = fileName;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.sha256Hash = sha256Hash;
        this.capturedAt = capturedAt != null ? capturedAt : Instant.now();
        this.createdAt = Instant.now();
        this.metadata = metadata;
    }

    public UUID getId() {
        return id;
    }

    public UUID getVisitId() {
        return visitId;
    }

    public EvidenceType getEvidenceType() {
        return evidenceType;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getSha256Hash() {
        return sha256Hash;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getMetadata() {
        return metadata;
    }
}
