package ar.com.planillero.supervision;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import ar.com.planillero.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Registro de turno operativo y telemetría de un operador.
 */
@Entity
@Table(name = "operator_shifts", schema = "visits", uniqueConstraints = @UniqueConstraint(
        name = "uk_operator_shifts_operator_date",
        columnNames = { "operator_id", "shift_date" }))
public class OperatorShift {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operator_id", nullable = false)
    private User operator;

    @Column(name = "shift_date", nullable = false)
    private LocalDate shiftDate;

    @Column(nullable = false)
    private String jurisdiction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShiftStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "last_heartbeat_at", nullable = false)
    private Instant lastHeartbeatAt;

    @Column(name = "last_latitude")
    private BigDecimal lastLatitude;

    @Column(name = "last_longitude")
    private BigDecimal lastLongitude;

    @Column(name = "battery_level")
    private BigDecimal batteryLevel;

    @Column(name = "network_status", nullable = false)
    private String networkStatus = "ONLINE";

    @Column
    private String observations;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OperatorShift() {
        // Requerido por JPA
    }

    public OperatorShift(User operator, LocalDate shiftDate, String jurisdiction, ShiftStatus status) {
        this.id = UUID.randomUUID();
        this.operator = operator;
        this.shiftDate = shiftDate;
        this.jurisdiction = jurisdiction;
        this.status = status;
        this.startedAt = Instant.now();
        this.lastHeartbeatAt = Instant.now();
        this.createdAt = Instant.now();
        this.networkStatus = "ONLINE";
    }

    public UUID getId() {
        return id;
    }

    public User getOperator() {
        return operator;
    }

    public LocalDate getShiftDate() {
        return shiftDate;
    }

    public String getJurisdiction() {
        return jurisdiction;
    }

    public ShiftStatus getStatus() {
        return status;
    }

    public void setStatus(ShiftStatus status) {
        this.status = status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public Instant getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public void setLastHeartbeatAt(Instant lastHeartbeatAt) {
        this.lastHeartbeatAt = lastHeartbeatAt;
    }

    public BigDecimal getLastLatitude() {
        return lastLatitude;
    }

    public void setLastLatitude(BigDecimal lastLatitude) {
        this.lastLatitude = lastLatitude;
    }

    public BigDecimal getLastLongitude() {
        return lastLongitude;
    }

    public void setLastLongitude(BigDecimal lastLongitude) {
        this.lastLongitude = lastLongitude;
    }

    public BigDecimal getBatteryLevel() {
        return batteryLevel;
    }

    public void setBatteryLevel(BigDecimal batteryLevel) {
        this.batteryLevel = batteryLevel;
    }

    public String getNetworkStatus() {
        return networkStatus;
    }

    public void setNetworkStatus(String networkStatus) {
        this.networkStatus = networkStatus != null ? networkStatus : "UNKNOWN";
    }

    public String getObservations() {
        return observations;
    }

    public void setObservations(String observations) {
        this.observations = observations;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
