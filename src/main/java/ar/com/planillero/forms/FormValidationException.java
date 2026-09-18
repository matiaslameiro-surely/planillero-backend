package ar.com.planillero.forms;

import java.util.List;

/**
 * El payload de un formulario no cumple el schema de su plantilla.
 *
 * <p>Transporta <strong>todas</strong> las violaciones encontradas, no la primera: el formulario
 * del cliente necesita marcar de una sola vez cada campo con problema.
 */
public class FormValidationException extends RuntimeException {

    private final transient List<FieldViolation> violations;

    public FormValidationException(List<FieldViolation> violations) {
        super("El formulario no cumple el schema de su plantilla.");
        this.violations = List.copyOf(violations);
    }

    public List<FieldViolation> getViolations() {
        return violations;
    }
}
