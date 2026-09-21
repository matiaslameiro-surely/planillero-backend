package ar.com.planillero.audit;

import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Fija el orden del advice de {@code @Transactional} un lugar antes que {@link AuditAspect}.
 *
 * <p>Sin esto los dos usan {@code LOWEST_PRECEDENCE} y el orden entre ellos queda indefinido. Con
 * esto la transacción de la mutación siempre envuelve al aspecto, y la fila de auditoría se
 * confirma o se revierte junto con la mutación (lo verifica {@code AuditAspectOrderIntegrationTest}).
 *
 * <p>Declarar {@code @EnableTransactionManagement} desactiva la configuración equivalente de Spring
 * Boot, así que se repite {@code proxyTargetClass = true}, que es el valor que Boot usa por defecto.
 */
@Configuration(proxyBeanMethods = false)
@EnableTransactionManagement(proxyTargetClass = true, order = AuditAspect.ORDER - 1)
public class AuditTransactionConfig {
}
