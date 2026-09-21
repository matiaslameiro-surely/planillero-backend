package ar.com.planillero.planning;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    // Evidencia del inicio de la visita. Es nula hasta que un operador la inicia.
    @Column(name = "start_latitude")
    private BigDecimal startLatitude;

    @Column(name = "start_longitude")
    private BigDecimal startLongitude;

    @Column(name = "start_accuracy_meters")
    private BigDecimal startAccuracyMeters;

    @Column(name = "started_at_device")
    private Instant startedAtDevice;

    @Column(name = "started_at_server")
    private Instant startedAtServer;

    @Column(name = "drift_seconds")
    private Long driftSeconds;

    @Column(name = "started_by")
    private UUID startedBy;

    // Origen del formulario de la visita. Las escribe la sincronización diferida
    // (ar.com.planillero.visits.VisitFormRecord); acá sólo se leen, para que el tablero del
    // supervisor pueda distinguir lo que llegó en línea de lo que llegó de una cola offline.
    @Column(name = "synced_deferred", nullable = false)
    private boolean syncedDeferred;

    @Column(name = "synced_at")
    private Instant syncedAt;

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

    public void setStatus(VisitStatus status) {
        this.status = status;
    }

    public VisitUrgency getUrgency() {
        return urgency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Inicia la visita: deja asentada la presencia del operador y pasa a {@code IN_PROGRESS}.
     *
     * <p>Sólo se puede iniciar una visita {@code ASSIGNED}. Los valores se guardan con la misma escala
     * que las columnas (6 decimales para coordenadas, 2 para la precisión), así lo que se devuelve al
     * cliente es exactamente lo que quedó persistido.
     *
     * @throws IllegalStateException si la visita no está en estado {@code ASSIGNED}
     */
    public void start(BigDecimal latitude, BigDecimal longitude, BigDecimal accuracyMeters,
            Instant startedAtDevice, Instant startedAtServer, long driftSeconds, UUID operatorId) {
        if (status != VisitStatus.ASSIGNED) {
            throw new IllegalStateException(
                    "Sólo se puede iniciar una visita ASSIGNED y ésta está " + status + ".");
        }
        this.startLatitude = latitude.setScale(6, RoundingMode.HALF_UP);
        this.startLongitude = longitude.setScale(6, RoundingMode.HALF_UP);
        this.startAccuracyMeters = accuracyMeters.setScale(2, RoundingMode.HALF_UP);
        this.startedAtDevice = startedAtDevice;
        this.startedAtServer = startedAtServer;
        this.driftSeconds = driftSeconds;
        this.startedBy = operatorId;
        this.status = VisitStatus.IN_PROGRESS;
    }

    public BigDecimal getStartLatitude() {
        return startLatitude;
    }

    public BigDecimal getStartLongitude() {
        return startLongitude;
    }

    public BigDecimal getStartAccuracyMeters() {
        return startAccuracyMeters;
    }

    public Instant getStartedAtDevice() {
        return startedAtDevice;
    }

    public Instant getStartedAtServer() {
        return startedAtServer;
    }

    public Long getDriftSeconds() {
        return driftSeconds;
    }

    public UUID getStartedBy() {
        return startedBy;
    }

    /** El formulario de esta visita llegó por sincronización diferida y no por la carga en línea. */
    public boolean isSyncedDeferred() {
        return syncedDeferred;
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }
}
