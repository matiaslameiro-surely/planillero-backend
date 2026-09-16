package ar.com.planillero.salud;

import java.time.Instant;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint de salud de la aplicación.
 *
 * <p>Sirve para confirmar que la aplicación levantó y responde. Es deliberadamente propio y no
 * Spring Boot Actuator: para un esqueleto, Actuator agrega superficie expuesta y decisiones de
 * seguridad que todavía no queremos tomar.
 */
@RestController
public class SaludController {

    /**
     * Estado de la aplicación.
     *
     * @return un mapa con el estado y el momento de la consulta, serializado a JSON
     */
    @GetMapping("/salud")
    public Map<String, String> salud() {
        return Map.of(
                "estado", "ok",
                "momento", Instant.now().toString());
    }
}
