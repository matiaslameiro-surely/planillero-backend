package ar.com.planillero.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca un método de servicio cuya ejecución exitosa se registra en {@code audit.audit_logs}.
 *
 * <p>La captura la hace {@link AuditAspect}: usuario e IP salen del contexto de seguridad y de la
 * request HTTP en curso, no de los parámetros del método. Sólo hace falta declarar qué evento es y
 * cuál de los parámetros identifica la entidad afectada.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AuditLog {

    /** Código estable del evento, por ejemplo {@code "VISIT_STARTED"}. */
    String eventType();

    /** Tipo de entidad afectada, por ejemplo {@code "VISIT"}. */
    String entityType();

    /** Índice (0-based) del parámetro del método que identifica la entidad auditada. */
    int entityIdParam() default 0;
}
