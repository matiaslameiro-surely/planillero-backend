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
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Intercepta los métodos marcados con {@link AuditLog} y registra el evento sólo si la operación
 * terminó bien: una mutación que lanzó una excepción no llegó a pasar, así que no hay nada que
 * auditar (y el intento fallido queda igual en los logs de aplicación, que es lo que cubre
 * {@link #onFailure}).
 *
 * <p>Sirve para mutaciones donde una invocación afecta a **una** entidad. Cuando una invocación
 * afecta a varias (por ejemplo, {@code PlanningService.assign} sobre un conjunto de visitas), esa
 * mutación no usa esta anotación: llama a {@link AuditChainService#append} directamente, una vez
 * por entidad, con {@link AuditRequestContext}.
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final AuditChainService chainService;
    private final AuditRequestContext requestContext;

    public AuditAspect(AuditChainService chainService, AuditRequestContext requestContext) {
        this.chainService = chainService;
        this.requestContext = requestContext;
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
        chainService.append(auditLog.eventType(), auditLog.entityType(), entityId,
                requestContext.currentUsername(), requestContext.currentIp(), requestContext.currentDeviceId(),
                payload);

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
}
