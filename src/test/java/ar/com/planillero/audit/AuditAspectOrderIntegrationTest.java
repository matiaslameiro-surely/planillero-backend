package ar.com.planillero.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.BeanFactoryTransactionAttributeSourceAdvisor;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import ar.com.planillero.AbstractIntegrationTest;

/**
 * Verifica el orden entre {@link AuditAspect} y {@code @Transactional}: el evento de auditoría tiene
 * que escribirse <b>dentro</b> de la transacción de la mutación, de modo que si ésta se revierte no
 * quede un evento de algo que no ocurrió.
 *
 * <p>La mutación de prueba marca su transacción como {@code rollback-only} después de devolver: el
 * aspecto escribe la fila justo antes de que la transacción cierre. Si la fila sobrevive, el aspecto
 * corrió por fuera y abrió su propia transacción.
 */
@Import(AuditAspectOrderIntegrationTest.ProbeConfig.class)
class AuditAspectOrderIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RollingBackMutation mutation;

    @Autowired
    private BeanFactoryTransactionAttributeSourceAdvisor transactionAdvisor;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void transactionAdviceIsExplicitlyOrderedOutsideTheAuditAspect() {
        // Un empate (ambos en LOWEST_PRECEDENCE) dejaría el orden librado a Spring: se exige que la
        // transacción tenga estrictamente mayor precedencia, es decir, un número menor.
        assertThat(transactionAdvisor.getOrder()).isLessThan(AuditAspect.ORDER);
    }

    @Test
    void auditEventIsRolledBackTogetherWithTheMutation() {
        String entityId = UUID.randomUUID().toString();

        mutation.mutate(entityId, true);

        assertThat(auditRows(entityId)).isZero();
    }

    @Test
    void auditEventIsCommittedTogetherWithTheMutation() {
        String entityId = UUID.randomUUID().toString();

        mutation.mutate(entityId, false);

        assertThat(auditRows(entityId)).isEqualTo(1);
    }

    private Integer auditRows(String entityId) {
        return jdbc.queryForObject("select count(*) from audit.audit_logs where entity_id = ?",
                Integer.class, entityId);
    }

    /** Servicio de prueba con la misma combinación de anotaciones que las mutaciones reales. */
    static class RollingBackMutation {

        @Transactional
        @AuditLog(eventType = "ORDER_PROBE", entityType = "PROBE")
        public String mutate(String entityId, boolean rollBack) {
            if (rollBack) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            }
            return entityId;
        }
    }

    @TestConfiguration
    static class ProbeConfig {

        @Bean
        RollingBackMutation rollingBackMutation() {
            return new RollingBackMutation();
        }
    }
}
