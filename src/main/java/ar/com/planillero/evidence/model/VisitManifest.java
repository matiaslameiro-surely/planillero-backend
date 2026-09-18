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
 * Entidad que representa el manifiesto criptográfico de cierre de visita pericial firmado con HMAC-SHA256.
 */
@Entity
@Table(name = "visit_manifests", schema = "visits")
public class VisitManifest {

    @Id
    private UUID id;

    @Column(name = "visit_id", nullable = false)
    private UUID visitId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "device_info", length = 255)
    private String deviceInfo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "manifest_data", columnDefinition = "jsonb", nullable = false)
    private String manifestData;

    @Column(name = "hmac_signature", nullable = false, length = 64)
    private String hmacSignature;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 30)
    private VerificationStatus verificationStatus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected VisitManifest() {
        // Constructor por defecto requerido por JPA.
    }

    public VisitManifest(
            UUID id,
            UUID visitId,
            UUID userId,
            String deviceInfo,
            String manifestData,
            String hmacSignature,
            VerificationStatus verificationStatus) {
        this.id = id != null ? id : UUID.randomUUID();
        this.visitId = visitId;
        this.userId = userId;
        this.deviceInfo = deviceInfo;
        this.manifestData = manifestData;
        this.hmacSignature = hmacSignature;
        this.verificationStatus = verificationStatus;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getVisitId() {
        return visitId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getDeviceInfo() {
        return deviceInfo;
    }

    public String getManifestData() {
        return manifestData;
    }

    public String getHmacSignature() {
        return hmacSignature;
    }

    public VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(VerificationStatus verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
