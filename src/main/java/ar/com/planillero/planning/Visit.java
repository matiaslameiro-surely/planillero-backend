package ar.com.planillero.planning;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Visita a un cliente: la unidad de trabajo que el supervisor asigna a un operador.
 *
 * <p>El {@code status} describe el ciclo de vida de la visita. Asignar una visita no lo muta: la
 * asignación vive en {@link RouteSheet}. Por eso {@code ASSIGNED} se considera tan asignable como
 * {@code PENDING} (una visita puede replanificarse), pero {@code COMPLETED} y {@code CANCELLED} no.
 */
@Entity
@Table(name = "visits", schema = "visits")
public class Visit {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private BigDecimal latitude;

    @Column(nullable = false)
    private BigDecimal longitude;

    @Column(nullable = false)
    private String jurisdiction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VisitStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VisitUrgency urgency;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Visit() {
        // Requerido por JPA.
    }

    public Visit(String code, String address, BigDecimal latitude, BigDecimal longitude,
            String jurisdiction, VisitUrgency urgency) {
        this.id = UUID.randomUUID();
        this.code = code;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.jurisdiction = jurisdiction;
        this.status = VisitStatus.PENDING;
        this.urgency = urgency;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getAddress() {
        return address;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public String getJurisdiction() {
        return jurisdiction;
    }

    public VisitStatus getStatus() {
        return status;
    }

    public VisitUrgency getUrgency() {
        return urgency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}