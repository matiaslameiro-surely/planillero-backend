package ar.com.planillero.roles;

import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de ejemplo para mostrar el RBAC en acción.
 *
 * <p>No tienen lógica de negocio: existen para poder probar que el control por rol funciona y para
 * servir de molde a los endpoints reales, que van a repetir este estilo de {@code @PreAuthorize}.
 */
@RestController
@RequestMapping("/api/v1/roles")
public class RoleExampleController {

    /** Accesible para cualquier rol autenticado. */
    @GetMapping("/ejemplo-operador")
    @PreAuthorize("hasAnyRole('OPERATOR', 'SUPERVISOR', 'ADMINISTRATOR')")
    public Map<String, String> ejemploOperador() {
        return Map.of("mensaje", "Acceso habilitado para operarios, supervisores y administradores.");
    }

    /** Sólo para administradores. */
    @GetMapping("/ejemplo-admin")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public Map<String, String> ejemploAdmin() {
        return Map.of("mensaje", "Acceso habilitado sólo para administradores.");
    }
}
