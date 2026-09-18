package ar.com.planillero.planning;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Hoja de ruta: qué visitas le tocan a un operador un día, en qué orden.
 *
 * <p>{@code position} es la posición en el recorrido; la arma el servicio por urgencia. La unicidad
 * sobre {@code (visit_id, route_date)} garantiza que una visita no se agenda dos veces el mismo día,
 * ni al mismo operador ni a dos distintos.
 */
@Entity
@Table(name = "route_sheets", schema = "visits", uniqueConstraints = @UniqueConstraint(
        name = "uk_route_sheet_visit_date",
        columnNames = { "visit_id", "route_date" }))
public class RouteSheet {

    @Id
    private UUID id;

    @Column(name = "operator_id", nullable = false)
    private UUID operatorId;

    @Column(name = "route_date", nullable = false)
    private LocalDate routeDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visit_id", nullable = false)
    private Visit visit;

    @Column(nullable = false)
    private Integer position;

    @Column(name = "assigned_by", nullable = false)
    private UUID assignedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RouteSheet() {
        // Requerido por JPA.
    }

    public RouteSheet(UUID operatorId, LocalDate routeDate, Visit visit, Integer position, UUID assignedBy) {
        this.id = UUID.randomUUID();
        this.operatorId = operatorId;
        this.routeDate = routeDate;
        this.visit = visit;
        this.position = position;
        this.assignedBy = assignedBy;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOperatorId() {
        return operatorId;
    }

    public LocalDate getRouteDate() {
        return routeDate;
    }

    public Visit getVisit() {
        return visit;
    }

    public Integer getPosition() {
        return position;
    }

    public UUID getAssignedBy() {
        return assignedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}