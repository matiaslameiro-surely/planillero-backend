package ar.com.planillero.planning;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a las hojas de ruta persistidas. */
public interface RouteSheetRepository extends JpaRepository<RouteSheet, UUID> {

    List<RouteSheet> findByOperatorIdAndRouteDateOrderByPositionAsc(UUID operatorId, LocalDate routeDate);

    List<RouteSheet> findByOperatorId(UUID operatorId);

    List<RouteSheet> findByRouteDate(LocalDate routeDate);

    List<RouteSheet> findByVisitIdInAndRouteDate(List<UUID> visitIds, LocalDate routeDate);
}