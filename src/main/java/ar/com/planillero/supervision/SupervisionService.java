package ar.com.planillero.supervision;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import ar.com.planillero.common.ApiException;
import ar.com.planillero.planning.RouteSheet;
import ar.com.planillero.planning.RouteSheetRepository;
import ar.com.planillero.planning.Visit;
import ar.com.planillero.planning.VisitRepository;
import ar.com.planillero.planning.VisitStatus;
import ar.com.planillero.supervision.dto.DashboardSummaryDto;
import ar.com.planillero.supervision.dto.HeartbeatRequest;
import ar.com.planillero.supervision.dto.HeartbeatResponse;
import ar.com.planillero.supervision.dto.OperatorStatusDto;
import ar.com.planillero.supervision.dto.SupervisionExceptionDto;
import ar.com.planillero.user.RoleName;
import ar.com.planillero.user.User;
import ar.com.planillero.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lógica de negocio analítica del Tablero Central de Supervisión.
 *
 * <p>Implementa control de acceso horizontal multi-tenant (OWASP A01):
 * supervisores restringidos a su jurisdicción, administradores con acceso GLOBAL.
 */
@Service
public class SupervisionService {

    private static final long OFFLINE_THRESHOLD_MINUTES = 10;
    private static final long SLA_DELAY_THRESHOLD_MINUTES = 45;
    private static final double LOW_BATTERY_THRESHOLD = 0.20;

    private final UserRepository userRepository;
    private final OperatorShiftRepository operatorShiftRepository;
    private final VisitRepository visitRepository;
    private final RouteSheetRepository routeSheetRepository;

    public SupervisionService(
            UserRepository userRepository,
            OperatorShiftRepository operatorShiftRepository,
            VisitRepository visitRepository,
            RouteSheetRepository routeSheetRepository) {
        this.userRepository = userRepository;
        this.operatorShiftRepository = operatorShiftRepository;
        this.visitRepository = visitRepository;
        this.routeSheetRepository = routeSheetRepository;
    }

    /**
     * Resumen de métricas analíticas y KPIs para el tablero central.
     */
    @Transactional(readOnly = true)
    public DashboardSummaryDto getDashboardSummary(LocalDate date, String username) {
        User caller = currentUser(username);
        LocalDate targetDate = date != null ? date : LocalDate.now();
        boolean isGlobal = "GLOBAL".equalsIgnoreCase(caller.getJurisdiction());
        String jurisdiction = isGlobal ? "GLOBAL" : caller.getJurisdiction();

        List<OperatorShift> shifts = isGlobal
                ? operatorShiftRepository.findByShiftDateWithOperator(targetDate)
                : operatorShiftRepository.findByJurisdictionAndShiftDateWithOperator(caller.getJurisdiction(), targetDate);

        // Actualizar dinámicamente estado de desconexión en memoria si no hubo latido
        Instant now = Instant.now();
        List<SupervisionExceptionDto> exceptions = new ArrayList<>();

        int inField = 0;
        int delayed = 0;
        int offline = 0;
        int completed = 0;

        for (OperatorShift shift : shifts) {
            ShiftStatus liveStatus = calculateLiveStatus(shift, now);
            switch (liveStatus) {
                case EN_CAMPO -> inField++;
                case DEMORADO -> {
                    delayed++;
                    exceptions.add(new SupervisionExceptionDto(
                            shift.getOperator().getId(),
                            shift.getOperator().getUsername(),
                            "OUT_OF_SLA",
                            "HIGH",
                            "Operador demorado en visita fuera de SLA",
                            now
                    ));
                }
                case OFFLINE -> {
                    offline++;
                    exceptions.add(new SupervisionExceptionDto(
                            shift.getOperator().getId(),
                            shift.getOperator().getUsername(),
                            "OFFLINE",
                            "MEDIUM",
                            "Sin latidos recibidos hace más de " + OFFLINE_THRESHOLD_MINUTES + " minutos",
                            now
                    ));
                }
                case TURNO_COMPLETO -> completed++;
            }

            if (shift.getBatteryLevel() != null && shift.getBatteryLevel().doubleValue() < LOW_BATTERY_THRESHOLD) {
                exceptions.add(new SupervisionExceptionDto(
                        shift.getOperator().getId(),
                        shift.getOperator().getUsername(),
                        "LOW_BATTERY",
                        "MEDIUM",
                        "Batería baja en dispositivo (" + Math.round(shift.getBatteryLevel().doubleValue() * 100) + "%)",
                        now
                ));
            }
        }

        // Consultar visitas según jurisdicción
        List<Visit> visits = isGlobal
                ? visitRepository.findAll()
                : visitRepository.findByJurisdictionOrderByCodeAsc(caller.getJurisdiction());

        int totalVisits = visits.size();
        int pendingVisits = 0;
        int inProgressVisits = 0;
        int completedVisits = 0;

        for (Visit v : visits) {
            if (v.getStatus() == VisitStatus.PENDING || v.getStatus() == VisitStatus.ASSIGNED) {
                pendingVisits++;
            } else if (v.getStatus() == VisitStatus.IN_PROGRESS) {
                inProgressVisits++;
            } else if (v.getStatus() == VisitStatus.COMPLETED) {
                completedVisits++;
            }
        }

        int totalOperators = shifts.size();
        double slaRate = totalOperators > 0
                ? Math.max(0.0, Math.min(100.0, Math.round(((double) (totalOperators - delayed) / totalOperators) * 1000.0) / 10.0))
                : 100.0;

        return new DashboardSummaryDto(
                targetDate,
                jurisdiction,
                totalOperators,
                inField,
                delayed,
                offline,
                completed,
                totalVisits,
                pendingVisits,
                inProgressVisits,
                completedVisits,
                slaRate,
                exceptions
        );
    }

    /**
     * Grilla reactiva con el estado en vivo de cada operador.
     */
    @Transactional(readOnly = true)
    public List<OperatorStatusDto> getOperatorsStatus(LocalDate date, String username) {
        User caller = currentUser(username);
        LocalDate targetDate = date != null ? date : LocalDate.now();
        boolean isGlobal = "GLOBAL".equalsIgnoreCase(caller.getJurisdiction());

        List<OperatorShift> shifts = isGlobal
                ? operatorShiftRepository.findByShiftDateWithOperator(targetDate)
                : operatorShiftRepository.findByJurisdictionAndShiftDateWithOperator(caller.getJurisdiction(), targetDate);

        List<RouteSheet> sheets = routeSheetRepository.findByRouteDate(targetDate);
        Map<UUID, List<RouteSheet>> sheetsByOperator = sheets.stream()
                .collect(Collectors.groupingBy(RouteSheet::getOperatorId));

        // Obtener visitas en curso
        List<Visit> inProgressVisits = isGlobal
                ? visitRepository.findAll().stream().filter(v -> v.getStatus() == VisitStatus.IN_PROGRESS).toList()
                : visitRepository.findByJurisdictionOrderByCodeAsc(caller.getJurisdiction()).stream()
                        .filter(v -> v.getStatus() == VisitStatus.IN_PROGRESS).toList();

        Map<UUID, Visit> activeVisitByOperator = inProgressVisits.stream()
                .filter(v -> v.getStartedBy() != null)
                .collect(Collectors.toMap(Visit::getStartedBy, v -> v, (v1, v2) -> v1));

        Instant now = Instant.now();
        List<OperatorStatusDto> result = new ArrayList<>();

        for (OperatorShift shift : shifts) {
            ShiftStatus liveStatus = calculateLiveStatus(shift, now);
            List<RouteSheet> opSheets = sheetsByOperator.getOrDefault(shift.getOperator().getId(), List.of());
            int assignedCount = opSheets.size();
            int completedCount = (int) opSheets.stream()
                    .filter(rs -> rs.getVisit().getStatus() == VisitStatus.COMPLETED)
                    .count();

            Visit activeVisit = activeVisitByOperator.get(shift.getOperator().getId());
            String activeVisitCode = null;
            String activeVisitAddress = null;
            Long activeElapsedMinutes = null;
            String slaStatus = "NO_ACTIVE_VISIT";

            if (activeVisit != null) {
                activeVisitCode = activeVisit.getCode();
                activeVisitAddress = activeVisit.getAddress();
                if (activeVisit.getStartedAtServer() != null) {
                    activeElapsedMinutes = Duration.between(activeVisit.getStartedAtServer(), now).toMinutes();
                    slaStatus = activeElapsedMinutes > SLA_DELAY_THRESHOLD_MINUTES ? "DELAYED" : "OK";
                } else {
                    slaStatus = "OK";
                }
            }

            result.add(new OperatorStatusDto(
                    shift.getOperator().getId(),
                    shift.getOperator().getUsername(),
                    shift.getJurisdiction(),
                    liveStatus,
                    shift.getBatteryLevel(),
                    shift.getNetworkStatus(),
                    shift.getLastHeartbeatAt(),
                    shift.getLastLatitude(),
                    shift.getLastLongitude(),
                    assignedCount,
                    completedCount,
                    activeVisitCode,
                    activeVisitAddress,
                    activeElapsedMinutes,
                    slaStatus,
                    shift.getObservations()
            ));
        }

        return result;
    }

    /**
     * Registra un latido periódico (heartbeat) enviado por el cliente móvil del operador.
     */
    @Transactional
    public HeartbeatResponse recordHeartbeat(HeartbeatRequest request, String username) {
        User operator = currentUser(username);
        requireOperator(operator);

        LocalDate today = LocalDate.now();
        Instant now = Instant.now();

        OperatorShift shift = operatorShiftRepository.findByOperatorIdAndShiftDate(operator.getId(), today)
                .orElseGet(() -> new OperatorShift(operator, today, operator.getJurisdiction(), ShiftStatus.EN_CAMPO));

        shift.setLastHeartbeatAt(now);
        if (request.batteryLevel() != null) {
            shift.setBatteryLevel(request.batteryLevel());
        }
        if (request.networkStatus() != null) {
            shift.setNetworkStatus(request.networkStatus());
        }
        if (request.latitude() != null) {
            shift.setLastLatitude(request.latitude());
        }
        if (request.longitude() != null) {
            shift.setLastLongitude(request.longitude());
        }
        if (request.observations() != null && !request.observations().isBlank()) {
            shift.setObservations(request.observations().trim());
        }

        // Evaluar estado operativo
        if (shift.getStatus() != ShiftStatus.TURNO_COMPLETO) {
            // Revisar si tiene alguna visita en curso que supere el SLA
            List<Visit> inProgress = visitRepository.findByJurisdictionOrderByCodeAsc(operator.getJurisdiction()).stream()
                    .filter(v -> v.getStatus() == VisitStatus.IN_PROGRESS && operator.getId().equals(v.getStartedBy()))
                    .toList();

            boolean isDelayed = false;
            for (Visit v : inProgress) {
                if (v.getStartedAtServer() != null && Duration.between(v.getStartedAtServer(), now).toMinutes() > SLA_DELAY_THRESHOLD_MINUTES) {
                    isDelayed = true;
                    break;
                }
            }

            if (isDelayed) {
                shift.setStatus(ShiftStatus.DEMORADO);
            } else if (shift.getStatus() == ShiftStatus.OFFLINE || shift.getStatus() == ShiftStatus.DEMORADO) {
                shift.setStatus(ShiftStatus.EN_CAMPO);
            }
        }

        operatorShiftRepository.save(shift);

        return new HeartbeatResponse(
                true,
                now,
                shift.getStatus(),
                "Latido registrado exitosamente"
        );
    }

    private ShiftStatus calculateLiveStatus(OperatorShift shift, Instant now) {
        if (shift.getStatus() == ShiftStatus.TURNO_COMPLETO) {
            return ShiftStatus.TURNO_COMPLETO;
        }
        long minutesSinceLastHeartbeat = Duration.between(shift.getLastHeartbeatAt(), now).toMinutes();
        if (minutesSinceLastHeartbeat > OFFLINE_THRESHOLD_MINUTES) {
            return ShiftStatus.OFFLINE;
        }
        return shift.getStatus();
    }

    private User currentUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> ApiException.unauthorized("USER_NOT_FOUND", "Usuario no encontrado: " + username));
    }

    private void requireOperator(User user) {
        boolean isOperator = user.getRoles().stream().anyMatch(r -> r.getName() == RoleName.OPERATOR);
        if (!isOperator) {
            throw ApiException.forbidden("NOT_AN_OPERATOR", "El usuario no tiene el rol de operador");
        }
    }
}
