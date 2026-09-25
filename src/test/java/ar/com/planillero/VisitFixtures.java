package ar.com.planillero;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Visitas y asignaciones de prueba, cargadas por JDBC.
 *
 * <p>Los endpoints por visita exigen que la visita sea del usuario (ver
 * {@link ar.com.planillero.planning.VisitAccessGuard}): una visita inventada con
 * {@code UUID.randomUUID()} responde 404, y una sin asignar responde 403 al operador. Estos helpers
 * arman el escenario mínimo con datos ficticios y los usuarios del seed.
 */
public final class VisitFixtures {

    /** Supervisor del seed ({@code supervisor.demo}), que figura como quien asignó. */
    private static final UUID SUPERVISOR_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

    private VisitFixtures() {
    }

    /** Crea una visita pendiente en la jurisdicción indicada y devuelve su identificador. */
    public static UUID createVisit(JdbcTemplate jdbc, String jurisdiction) {
        UUID id = UUID.randomUUID();
        createVisit(jdbc, id, "T-" + id.toString().substring(0, 8), jurisdiction);
        return id;
    }

    /** Crea una visita pendiente con identificador y código fijos. */
    public static void createVisit(JdbcTemplate jdbc, UUID id, String code, String jurisdiction) {
        jdbc.update("""
                insert into visits.visits
                    (id, code, address, latitude, longitude, jurisdiction, status, urgency)
                values (?, ?, 'Calle Ficticia 100', -34.600000, -58.400000, ?, 'PENDING', 'LOW')
                """, id, code, jurisdiction);
    }

    /**
     * Pone la visita en la hoja de ruta de hoy del operador. No cambia el estado de la visita: los
     * tests que la usan esperan encontrarla como la crearon.
     *
     * <p>Es idempotente, para los tests que arman sus visitas fijas en cada {@code @BeforeEach}: si la
     * visita ya está en una hoja de hoy, no hace nada.
     */
    public static void assign(JdbcTemplate jdbc, UUID visitId, String operatorUsername) {
        jdbc.update("""
                insert into visits.route_sheets (id, operator_id, route_date, visit_id, position, assigned_by)
                select ?, u.id, current_date, ?, 1, ? from core.users u where u.username = ?
                on conflict (visit_id, route_date) do nothing
                """, UUID.randomUUID(), visitId, SUPERVISOR_ID, operatorUsername);
    }

    /** Crea una visita en la jurisdicción indicada y se la asigna al operador. */
    public static UUID createAssignedVisit(JdbcTemplate jdbc, String jurisdiction, String operatorUsername) {
        UUID id = createVisit(jdbc, jurisdiction);
        assign(jdbc, id, operatorUsername);
        return id;
    }
}
