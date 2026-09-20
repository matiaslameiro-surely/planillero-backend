package ar.com.planillero.audit;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Usuario, IP y dispositivo de la request en curso, para quien escribe en {@code audit.audit_logs}.
 *
 * <p>Lo usa {@link AuditAspect} para las mutaciones de un solo método, y cualquier servicio que
 * necesite emitir varias filas de auditoría por invocación (por ejemplo, una asignación en bloque
 * que audita cada visita por separado) en vez de una sola vía la anotación {@link AuditLog}.
 */
@Component
public class AuditRequestContext {

    private static final String DEVICE_HEADER = "X-Device-Id";

    /** Usuario autenticado, o {@code "system"} si no hay ninguno (por ejemplo, en un test directo). */
    public String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "system";
    }

    /** {@code null} fuera de una request HTTP. */
    public String currentIp() {
        HttpServletRequest request = currentRequest();
        return request != null ? request.getRemoteAddr() : null;
    }

    /** {@code null} fuera de una request HTTP, o si el cliente no mandó el header. */
    public String currentDeviceId() {
        HttpServletRequest request = currentRequest();
        return request != null ? request.getHeader(DEVICE_HEADER) : null;
    }

    private HttpServletRequest currentRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        return attrs.getRequest();
    }
}
