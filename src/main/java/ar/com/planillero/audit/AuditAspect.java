package ar.com.planillero.audit;

import java.util.LinkedHashMap;
import java.util.Map;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Intercepta los métodos marcados con {@link AuditLog} y registra el evento sólo si la operación
 * terminó bien: una mutación que lanzó una excepción no llegó a pasar, así que no hay nada que
 * auditar (y el intento fallido queda igual en los logs de aplicación, que es lo que cubre
 * {@link #onFailure}).
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);
    private static final String DEVICE_HEADER = "X-Device-Id";

    private final AuditChainService chainService;

    public AuditAspect(AuditChainService chainService) {
        this.chainService = chainService;
    }

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        Object[] args = joinPoint.getArgs();
        Object result = joinPoint.proceed();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("method", ((MethodSignature) joinPoint.getSignature()).getMethod().getName());
        payload.put("args", describeArgs(args));
        payload.put("result", result);

        String entityId = entityId(args, auditLog.entityIdParam());
        String username = currentUsername();
        String ip = currentIp();
        String deviceId = currentDeviceId();

        chainService.append(auditLog.eventType(), auditLog.entityType(), entityId, username, ip, deviceId, payload);

        return result;
    }

    /** No bloquea ni enmascara la excepción real: sólo deja rastro en el log de aplicación. */
    @AfterThrowing(pointcut = "@annotation(auditLog)", throwing = "ex")
    public void onFailure(ProceedingJoinPoint joinPoint, AuditLog auditLog, Throwable ex) {
        log.warn("Operación auditable '{}' falló y no se registró en audit_logs: {}",
                auditLog.eventType(), ex.getMessage());
    }

    private Map<String, Object> describeArgs(Object[] args) {
        Map<String, Object> described = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            described.put("arg" + i, describeArg(args[i]));
        }
        return described;
    }

    /** Un {@link MultipartFile} nunca se serializa entero: sólo sus metadatos. */
    private Object describeArg(Object arg) {
        if (arg instanceof MultipartFile file) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("filename", file.getOriginalFilename());
            meta.put("contentType", file.getContentType());
            meta.put("size", file.getSize());
            return meta;
        }
        return arg;
    }

    private String entityId(Object[] args, int entityIdParam) {
        if (entityIdParam < 0 || entityIdParam >= args.length || args[entityIdParam] == null) {
            return null;
        }
        return args[entityIdParam].toString();
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "system";
    }

    private String currentIp() {
        HttpServletRequest request = currentRequest();
        return request != null ? request.getRemoteAddr() : null;
    }

    private String currentDeviceId() {
        HttpServletRequest request = currentRequest();
        return request != null ? request.getHeader(DEVICE_HEADER) : null;
    }

    /** {@code null} fuera de una request HTTP (por ejemplo, un test que llama al service directo). */
    private HttpServletRequest currentRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        return attrs.getRequest();
    }
}
