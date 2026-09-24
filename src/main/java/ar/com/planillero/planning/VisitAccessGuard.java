package ar.com.planillero.planning;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import ar.com.planillero.common.ApiException;
import ar.com.planillero.user.Role;
import ar.com.planillero.user.RoleName;
import ar.com.planillero.user.User;
import ar.com.planillero.user.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Control de acceso horizontal (OWASP A01) de los endpoints que operan sobre una visita puntual:
 * evidencias, manifiesto, formulario y sincronización diferida.
 *
 * <p>El {@code @PreAuthorize} de cada controlador asegura el rol; esta guardia asegura que la visita
 * sea del usuario. La regla, con más de un rol vale el más amplio:
 * <ul>
 *   <li>administrador: cualquier visita;</li>
 *   <li>supervisor: sólo las de su jurisdicción;</li>
 *   <li>operador: sólo las que tiene en alguna hoja de ruta propia, igual que {@link VisitStartService}.</li>
 * </ul>
 *
 * <p>Se llama primero, antes de validar el cuerpo o escribir nada: una operación rechazada no puede
 * dejar un binario en el storage WORM ni una fila en la base.
 */
@Component
public class VisitAccessGuard {

    private final UserRepository userRepository;
    private final VisitRepository visitRepository;
    private final RouteSheetRepository routeSheetRepository;

    public VisitAccessGuard(UserRepository userRepository, VisitRepository visitRepository,
            RouteSheetRepository routeSheetRepository) {
        this.userRepository = userRepository;
        this.visitRepository = visitRepository;
        this.routeSheetRepository = routeSheetRepository;
    }

    /**
     * Devuelve la visita si el usuario puede operar sobre ella.
     *
     * @throws ApiException {@code 401} si la sesión no corresponde a ningún usuario,
     *                      {@code 404 visit_not_found} si la visita no existe,
     *                      {@code 403 outside_jurisdiction} o {@code 403 visit_not_assigned} si no
     *                      tiene acceso
     */
    @Transactional(readOnly = true)
    public Visit requireAccess(UUID visitId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> ApiException.unauthorized("unauthorized", "Sesión inválida."));
        // La existencia se resuelve antes que el acceso, como en el inicio de visita.
        Visit visit = visitRepository.findById(visitId)
                .orElseThrow(() -> ApiException.notFound("visit_not_found", "No existe la visita indicada."));

        Set<RoleName> roles = user.getRoles().stream().map(Role::getName).collect(Collectors.toSet());

        if (roles.contains(RoleName.ADMINISTRATOR)) {
            return visit;
        }
        if (roles.contains(RoleName.SUPERVISOR)) {
            if (!visit.getJurisdiction().equals(user.getJurisdiction())) {
                throw ApiException.forbidden("outside_jurisdiction",
                        "No tenés jurisdicción sobre las visitas u operadores de esa zona.");
            }
            return visit;
        }
        if (roles.contains(RoleName.OPERATOR)
                && routeSheetRepository.existsByOperatorIdAndVisitId(user.getId(), visitId)) {
            return visit;
        }
        throw ApiException.forbidden("visit_not_assigned", "La visita no está asignada a este operador.");
    }
}
